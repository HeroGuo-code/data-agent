# -*- coding: utf-8 -*-
import psycopg2, requests, json, re, os, time
URL=os.environ.get("OPENAI_BASE_URL","https://api.openai.com")+"/v1/chat/completions"; KEY=os.environ.get("OPENAI_API_KEY",""); MODEL=os.environ.get("OPENAI_MODEL","deepseek-v4-flash")
SYS="You are a strict machine translator. Translate each English string in the input JSON array into natural Simplified Chinese. Output ONLY a JSON array of strings, same order and same length. Preserve any tokens wrapped in backticks (like `Column Name`) and all identifiers/SQL inside them EXACTLY as-is. No explanations, no markdown, no code fences."
def is_zh(s): return bool(re.search(r'[\u4e00-\u9fff]', s))
def translate_batch(items, retries=3, batch=25):
    out=[]
    for i in range(0,len(items),batch):
        chunk=items[i:i+batch]
        if not chunk: continue
        res=None
        for a in range(retries):
            try:
                r=requests.post(URL,headers={"Authorization":"Bearer "+KEY},json={"model":MODEL,"messages":[{"role":"system","content":SYS},{"role":"user","content":json.dumps(chunk,ensure_ascii=False)}],"temperature":0},timeout=120)
                r.raise_for_status(); c=r.json()["choices"][0]["message"]["content"]
                m=re.search(r'\[.*\]',c,re.S)
                arr=json.loads(m.group(0)) if m else None
                if arr is not None and len(arr)==len(chunk):
                    res=arr; break
            except Exception as e:
                print("  retry",a,repr(str(e)[:80]))
            time.sleep(2)
        if res is None:
            raise RuntimeError("batch failed")
        out.extend(res); time.sleep(0.3)
    return out

conn=psycopg2.connect(host="localhost",dbname="data_agent_tutorial",user="postgres",password="postgres",port=5432)
cur=conn.cursor()

print("== glossary term/description/synonyms ==")
rows=cur.execute("select id, term, description, synonyms from glossary_knowledge order by id") or cur.fetchall()
for fld,idx in (("term",1),("description",2),("synonyms",3)):
    pairs=[(r[0],r[idx]) for r in rows if r[idx] and str(r[idx]).strip() and not is_zh(str(r[idx]))]
    if not pairs:
        print("  ",fld,"none to translate"); continue
    tr=translate_batch([p[1] for p in pairs])
    for (iid,_),t in zip(pairs,tr):
        cur.execute("update glossary_knowledge set %s=%%s where id=%%s"%fld,(t,str(iid)))
    conn.commit(); print("  done",fld,len(pairs))

print("== question_knowledge.question ==")
rows=cur.execute("select id, question from question_knowledge order by id") or cur.fetchall()
pairs=[(r[0],r[1]) for r in rows if not is_zh(str(r[1]))]
print("  to translate:",len(pairs))
tr=translate_batch([p[1] for p in pairs])
for (iid,_),t in zip(pairs,tr):
    cur.execute("update question_knowledge set question=%s where id=%s",(t,str(iid)))
conn.commit(); print("  done",len(pairs))

print("== vector_store.content ==")
rows=cur.execute("select id, content from vector_store order by id") or cur.fetchall()
pairs=[(r[0],r[1]) for r in rows if not is_zh(str(r[1]))]
print("  to translate:",len(pairs))
tr=translate_batch([p[1] for p in pairs])
for (iid,_),t in zip(pairs,tr):
    cur.execute("update vector_store set content=%s where id=%s",(t,str(iid)))
conn.commit(); print("  done",len(pairs))

cur.close(); conn.close(); print("ALL DONE")