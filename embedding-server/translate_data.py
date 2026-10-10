# -*- coding: utf-8 -*-
import psycopg2, requests, json, re, os, time

URL=os.environ.get("OPENAI_BASE_URL","https://api.openai.com")+"/v1/chat/completions"
KEY=os.environ.get("OPENAI_API_KEY","")
MODEL=os.environ.get("OPENAI_MODEL","deepseek-v4-flash")
SYS="You are a strict machine translator. Translate each English string in the input JSON array into natural Simplified Chinese. Output ONLY a JSON array of strings, same order and same length as input. Preserve any tokens wrapped in backticks (like `Column Name`) and all SQL keywords/identifiers inside them EXACTLY as-is, unchanged. No explanations, no markdown, no code fences, no extra text."

def translate_batch(items, retries=3, batch=25):
    out=[]
    for i in range(0, len(items), batch):
        chunk=items[i:i+batch]
        if not chunk:
            continue
        result=None
        for attempt in range(retries):
            try:
                r=requests.post(URL, headers={"Authorization":"Bearer "+KEY},
                                json={"model":MODEL,"messages":[{"role":"system","content":SYS},{"role":"user","content":json.dumps(chunk,ensure_ascii=False)}],"temperature":0},
                                timeout=120)
                r.raise_for_status()
                c=r.json()["choices"][0]["message"]["content"]
                m=re.search(r'\[.*\]', c, re.S)
                arr=json.loads(m.group(0)) if m else None
                if arr is not None and len(arr)==len(chunk):
                    result=arr
                    break
                else:
                    print("  parse mismatch, retry", attempt, repr(c[:120]))
            except Exception as e:
                print("  err", e, "retry", attempt)
            time.sleep(2)
        if result is None:
            print("  FAILED batch, raising")
            raise RuntimeError("translation failed")
        out.extend(result)
        time.sleep(0.3)
    return out

def fetch(cur, q):
    cur.execute(q); return cur.fetchall()

conn=psycopg2.connect(host="localhost",dbname="data_agent_tutorial",user="postgres",password="postgres",port=5432)
cur=conn.cursor()

# 1) db_table.description
print("== db_table.description ==")
rows=fetch(cur,"select id, description from db_table order by id")
ids=[r[0] for r in rows]; texts=[r[1] for r in rows]
tr=translate_batch(texts)
for iid,t in zip(ids,tr):
    cur.execute("update db_table set description=%s where id=%s",(t,str(iid)))
conn.commit(); print("  done", len(ids))

# 2) db_column.description
print("== db_column.description ==")
rows=fetch(cur,"select id, description from db_column order by id")
ids=[r[0] for r in rows]; texts=[r[1] for r in rows]
tr=translate_batch(texts)
for iid,t in zip(ids,tr):
    cur.execute("update db_column set description=%s where id=%s",(t,str(iid)))
conn.commit(); print("  done", len(ids))

# 3) glossary_knowledge (term, description, synonyms)
print("== glossary_knowledge ==")
rows=fetch(cur,"select id, term, description, synonyms from glossary_knowledge order by id")
for fld in ("term","description","synonyms"):
    idx={"term":1,"description":2,"synonyms":3}[fld]
    pairs=[(r[0], r[idx]) for r in rows if r[idx] and str(r[idx]).strip()]
    if not pairs:
        print("  ", fld, "skip (no data)"); continue
    texts=[p[1] for p in pairs]
    tr=translate_batch(texts)
    for (iid,_),t in zip(pairs,tr):
        cur.execute("update glossary_knowledge set %s=%%s where id=%%s" % fld,(t,str(iid)))
    conn.commit(); print("  done", fld, len(pairs))

# 4) question_knowledge.question
print("== question_knowledge.question ==")
rows=fetch(cur,"select id, question from question_knowledge order by id")
ids=[r[0] for r in rows]; texts=[r[1] for r in rows]
tr=translate_batch(texts)
for iid,t in zip(ids,tr):
    cur.execute("update question_knowledge set question=%s where id=%s",(t,str(iid)))
conn.commit(); print("  done", len(ids))

# 5) vector_store.content
print("== vector_store.content ==")
rows=fetch(cur,"select id, content from vector_store order by id")
ids=[r[0] for r in rows]; texts=[r[1] for r in rows]
tr=translate_batch(texts)
for iid,t in zip(ids,tr):
    cur.execute("update vector_store set content=%s where id=%s",(t,str(iid)))
conn.commit(); print("  done", len(ids))

cur.close(); conn.close()
print("ALL DONE")