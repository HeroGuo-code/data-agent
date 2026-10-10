import os
from flask import Flask, request, jsonify
from fastembed import TextEmbedding

MODEL = os.environ.get("EMBED_MODEL", "intfloat/multilingual-e5-large")
app = Flask(__name__)
_embedding = None

def get_model():
    global _embedding
    if _embedding is None:
        print(f"Loading embedding model {MODEL} ...", flush=True)
        _embedding = TextEmbedding(model_name=MODEL, cache_dir=os.environ.get("FASTEMBED_CACHE_DIR") or None, local_files_only=True)
        # warm up so first real request is fast
        _embedding.embed(["warmup"])
        print("Embedding model ready.", flush=True)
    return _embedding

def parse_inputs(payload):
    inp = payload.get("input")
    if isinstance(inp, str):
        return [inp]
    if isinstance(inp, list):
        return [str(x) for x in inp]
    return []

@app.route("/v1/embeddings", methods=["POST"])
def embeddings():
    payload = request.get_json(silent=True) or {}
    texts = parse_inputs(payload)
    model = get_model()
    vectors = list(model.embed(texts))
    data = [
        {"object": "embedding", "index": i, "embedding": [float(x) for x in vec]}
        for i, vec in enumerate(vectors)
    ]
    return jsonify({"object": "list", "data": data, "model": payload.get("model", MODEL), "usage": {"prompt_tokens": 0, "total_tokens": 0}})

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "model": MODEL})

if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    print(f"Starting embedding server on port {port}", flush=True)
    get_model()
    app.run(host="0.0.0.0", port=port, threaded=True)