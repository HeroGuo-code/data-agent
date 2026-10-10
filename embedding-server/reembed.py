import psycopg2, requests, time

conn = psycopg2.connect(host="localhost", dbname="data_agent_tutorial", user="postgres", password="postgres", port=5432)
cur = conn.cursor()
cur.execute("select id, content from vector_store order by id")
rows = cur.fetchall()
print("total rows:", len(rows))
EMB = "http://localhost:8000/v1/embeddings"

def embed(texts, batch=32):
    out = {}
    for i in range(0, len(texts), batch):
        chunk = texts[i:i+batch]
        r = requests.post(EMB, json={"input": chunk, "model": "intfloat/multilingual-e5-large"}, timeout=120)
        r.raise_for_status()
        data = r.json()["data"]
        for j, item in enumerate(data):
            out[i + j] = item["embedding"]
    return out

vecs = embed([c for _, c in rows])
for (id, _), (k, vec) in zip(rows, sorted(vecs.items())):
    # build vector literal '[...]'
    lit = "[" + ",".join(repr(float(x)) for x in vec) + "]"
    cur.execute("update vector_store set embedding = %s::vector where id = %s", (lit, str(id)))
conn.commit()
# verify dimension + non-null
cur.execute("select count(*), min(vector_dims(embedding)), max(vector_dims(embedding)) from vector_store")
print("verify:", cur.fetchone())
cur.close(); conn.close()
print("done")