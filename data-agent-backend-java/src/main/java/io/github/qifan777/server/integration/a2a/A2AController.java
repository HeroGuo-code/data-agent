package io.github.qifan777.server.integration.a2a;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import io.a2a.common.A2AHeaders;
import io.a2a.server.ServerCallContext;
import io.a2a.server.auth.UnauthenticatedUser;
import io.a2a.server.extensions.A2AExtensions;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.DeleteTaskPushNotificationConfigRequest;
import io.a2a.spec.GetAuthenticatedExtendedCardRequest;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.IdJsonMappingException;
import io.a2a.spec.InternalError;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InvalidParamsJsonMappingException;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.JSONRPCRequest;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.JSONParseError;
import io.a2a.spec.ListTaskPushNotificationConfigRequest;
import io.a2a.spec.MethodNotFoundError;
import io.a2a.spec.MethodNotFoundJsonMappingException;
import io.a2a.spec.NonStreamingJSONRPCRequest;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.StreamingJSONRPCRequest;
import io.a2a.spec.AgentCard;
import io.a2a.spec.TaskResubscriptionRequest;
import io.a2a.spec.UnsupportedOperationError;
import io.a2a.transport.jsonrpc.handler.JSONRPCHandler;
import io.a2a.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.reactivestreams.FlowAdapters;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
public class A2AController {
    private static final String METHOD_NAME_KEY = "methodName";
    private static final String HEADERS_KEY = "headers";

    private final HttpServletRequest request;
    private final JSONRPCHandler jsonRpcHandler;

    public A2AController(HttpServletRequest request, JSONRPCHandler jsonRpcHandler) {
        this.request = request;
        this.jsonRpcHandler = jsonRpcHandler;
    }

    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentJson() {
        return jsonRpcHandler.getAgentCard();
    }

    @PostMapping(value = "/a2a/jsonrpc", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object handleRequest(@RequestBody String body) {
        boolean streaming = false;
        ServerCallContext context = createCallContext();
        JSONRPCResponse<?> nonStreamingResponse = null;
        Flux<? extends JSONRPCResponse<?>> streamingResponse = null;
        JSONRPCErrorResponse error = null;

        try {
            var node = Utils.OBJECT_MAPPER.readTree(body);
            var method = node.get("method");
            streaming = method != null && (SendStreamingMessageRequest.METHOD.equals(method.asText())
                || TaskResubscriptionRequest.METHOD.equals(method.asText()));
            String methodName = (method != null && method.isTextual()) ? method.asText() : null;
            if (methodName != null) {
                context.getState().put(METHOD_NAME_KEY, methodName);
            }
            if (streaming) {
                var req = Utils.OBJECT_MAPPER.treeToValue(node, StreamingJSONRPCRequest.class);
                streamingResponse = processStreamingRequest(req, context);
            } else {
                var req = Utils.OBJECT_MAPPER.treeToValue(node, NonStreamingJSONRPCRequest.class);
                nonStreamingResponse = processNonStreamingRequest(req, context);
            }
        } catch (JsonProcessingException e) {
            error = handleError(e);
        } catch (Throwable t) {
            error = new JSONRPCErrorResponse(new InternalError(t.getMessage()));
        }

        if (error != null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Utils.toJsonString(error));
        }
        if (streaming) {
            SseEmitter emitter = new SseEmitter(0L);
            Flux<? extends JSONRPCResponse<?>> flux = streamingResponse != null
                ? streamingResponse
                : Flux.just(new JSONRPCErrorResponse(new InternalError("Streaming response is null")));
            flux.subscribe(
                response -> {
                    try {
                        emitter.send(SseEmitter.event().data(Utils.toJsonString(response)));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                e -> {
                    emitter.completeWithError(e);
                },
                emitter::complete);
            return emitter;
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Utils.toJsonString(nonStreamingResponse));
    }

    private JSONRPCErrorResponse handleError(JsonProcessingException exception) {
        Object id = null;
        JSONRPCError jsonRpcError;
        if (exception.getCause() instanceof JsonParseException) {
            jsonRpcError = new JSONParseError();
        } else if (exception instanceof JsonEOFException e) {
            jsonRpcError = new JSONParseError(e.getOriginalMessage());
        } else if (exception instanceof MethodNotFoundJsonMappingException e) {
            id = e.getId();
            jsonRpcError = new MethodNotFoundError();
        } else if (exception instanceof InvalidParamsJsonMappingException e) {
            id = e.getId();
            jsonRpcError = new InvalidParamsError();
        } else if (exception instanceof IdJsonMappingException e) {
            id = e.getId();
            jsonRpcError = new InvalidRequestError();
        } else {
            jsonRpcError = new InvalidRequestError();
        }
        return new JSONRPCErrorResponse(id, jsonRpcError);
    }

    private JSONRPCResponse<?> processNonStreamingRequest(NonStreamingJSONRPCRequest<?> req, ServerCallContext context) {
        if (req instanceof GetTaskRequest r) return jsonRpcHandler.onGetTask(r, context);
        if (req instanceof CancelTaskRequest r) return jsonRpcHandler.onCancelTask(r, context);
        if (req instanceof SetTaskPushNotificationConfigRequest r) return jsonRpcHandler.setPushNotificationConfig(r, context);
        if (req instanceof GetTaskPushNotificationConfigRequest r) return jsonRpcHandler.getPushNotificationConfig(r, context);
        if (req instanceof SendMessageRequest r) return jsonRpcHandler.onMessageSend(r, context);
        if (req instanceof ListTaskPushNotificationConfigRequest r) return jsonRpcHandler.listPushNotificationConfig(r, context);
        if (req instanceof DeleteTaskPushNotificationConfigRequest r) return jsonRpcHandler.deletePushNotificationConfig(r, context);
        if (req instanceof GetAuthenticatedExtendedCardRequest r) return jsonRpcHandler.onGetAuthenticatedExtendedCardRequest(r, context);
        return generateErrorResponse(req, new UnsupportedOperationError());
    }

    private Flux<? extends JSONRPCResponse<?>> processStreamingRequest(JSONRPCRequest<?> req, ServerCallContext context) {
        if (req instanceof SendStreamingMessageRequest r) {
            return Flux.from(FlowAdapters.toPublisher(jsonRpcHandler.onMessageSendStream(r, context)));
        }
        if (req instanceof TaskResubscriptionRequest r) {
            return Flux.from(FlowAdapters.toPublisher(jsonRpcHandler.onResubscribeToTask(r, context)));
        }
        return Flux.just(generateErrorResponse(req, new UnsupportedOperationError()));
    }

    private JSONRPCResponse<?> generateErrorResponse(JSONRPCRequest<?> request, JSONRPCError error) {
        return new JSONRPCErrorResponse(request.getId(), error);
    }

    private ServerCallContext createCallContext() {
        var user = UnauthenticatedUser.INSTANCE;
        Map<String, Object> state = new HashMap<>();
        Map<String, String> requestHeaders = new HashMap<>();
        java.util.Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            requestHeaders.put(name, request.getHeader(name));
        }
        state.put(HEADERS_KEY, requestHeaders);
        List<String> extensionHeaderValues = new ArrayList<>();
        java.util.Enumeration<String> extHeaders = request.getHeaders(A2AHeaders.X_A2A_EXTENSIONS);
        while (extHeaders != null && extHeaders.hasMoreElements()) {
            extensionHeaderValues.add(extHeaders.nextElement());
        }
        var requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaderValues);
        return new ServerCallContext(user, state, requestedExtensions);
    }
}
