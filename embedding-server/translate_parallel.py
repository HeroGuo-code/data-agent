# -*- coding: utf-8 -*-
import psycopg2, requests, json, re, os, time
from concurrent.futures import ThreadPoolExecutor

URL=os.environ.get("OPENAI_BASE_URL","https://api.openai.com")+"/v1/chat/completions"; KEY=os.environ.get("OPENAI_API_KEY",""); MODEL=os.environ.get("OPENAI_MODEL","deepseek-v4-flash")
SYS=("You are a strict machine translator. Translate each English string in the input JSON array into natural "
     "Simplified Chinese. Output ONLY a JSON array of strings, same order and same length. Preserve any tokens wrapped "
     "in backticks (like `Column Name`) and all identifiers/SQL inside them EXACTLY as-is. No explanations, no markdown, "
     "no code fences.")
def is_zh(s): return bool(re.search(r'[\u4e00-\u9fff]', s))
def call_batch(chunk):
    for a in range(3):
        try:
            r=requests.post(URL,headers={"Authorization":"Bearer "+KEY},json={"model":MODEL,"messages":[{"role":"system","content":SYS},{"role":"user","content":json.dumps(chunk,ensure_ascii=False)}],"temperature":0},timeout=60)
            r.raise_for_status()
            c=r.json()["choices"][0]["message"]["content"]
            m=re.search(r'\[.*\]',c,re.S)
            arr=json.loads(m.group(0)) if m else None
            if arr is not None and len(arr)==len(chunk):
                return arr
        except Exception as e:
            print("  batch err",repr(str(e)[:80]),"attempt",a,flush=True)
        time.sleep(2)
    return None

def translate_list(pairs, batch=25, workers=6):
    """pairs: list of (id, text). Returns list of (id, translated). Skips failures."""
    results=[None]*len(pairs)
    chunks=[(i,pairs[i:i+batch]) for i in range(0,len(pairs),batch)]
    def work(it):
        idx,chunk=it
        arr=call_batch([c[1] for c in chunk])
        if arr is not None:
            for j,trans in enumerate(arr):
                results[idx+j]=(chunk[j][0],trans)
    with ThreadPoolExecutor(max_workers=workers) as ex:
        list(ex.map(work, chunks))
    ok=[r for r in results if r is not None]
    print("  translated",len(ok),"/",len(pairs),"failed",len(pairs)-len(ok),flush=True)
    return ok

conn=psycopg2.connect(host="localhost",dbname="data_agent_tutorial",user="postgres",password="postgres",port=5432)
cur=conn.cursor()

print("== glossary (remaining) ==",flush=True)
rows=cur.execute("select id, term, description, synonyms from glossary_knowledge order by id") or cur.fetchall()
for fld,idx in (("term",1),("description",2),("synonyms",3)):
    pairs=[(r[0],r[idx]) for r in rows if r[idx] and str(r[idx]).strip() and not is_zh(str(r[idx]))]
    if not pairs:
        print("  ",fld,"done/none",flush=True); continue
    ok=translate_list(pairs)
    for iid,t in ok:
        cur.execute("update glossary_knowledge set %s=%%s where id=%%s"%fld,(t,str(iid)))
    conn.commit(); print("  committed",fld,flush=True)

print("== question_knowledge.question ==",flush=True)
rows=cur.execute("select id, question from question_knowledge order by id") or cur.fetchall()
pairs=[(r[0],r[1]) for r in rows if not is_zh(str(r[1]))]
print("  to translate",len(pairs),flush=True)
ok=translate_list(pairs)
for iid,t in ok:
    cur.execute("update question_knowledge set question=%s where id=%s",(t,str(iid)))
conn.commit(); print("  committed questions",flush=True)

print("== vector_store.content ==",flush=True)
rows=cur.execute("select id, content from vector_store order by id") or cur.fetchall()
pairs=[(r[0],r[1]) for r in rows if not is_zh(str(r[1]))]
print("  to translate",len(pairs),flush=True)
ok=translate_list(pairs)
for iid,t in ok:
    cur.execute("update vector_store set content=%s where id=%s",(t,str(iid)))
conn.commit(); print("  committed vector",flush=True)

cur.close(); conn.close(); print("ALL DONE",flush=True)