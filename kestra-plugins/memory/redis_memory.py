"""Redis Memory Store Plugin"""
import os, json, redis
from typing import List, Dict

class RedisMemory:
    def __init__(self, host: str = "localhost", port: int = 6379, password: str = None, db: int = 0):
        self.host = host
        self.port = port
        self.password = password or os.environ.get('REDIS_PASSWORD')
        self.db = db
    
    def save_message(self, session_id: str, role: str, content: str):
        try:
            r = redis.Redis(host=self.host, port=self.port, password=self.password, db=self.db)
            message = {"role": role, "content": content}
            r.lpush(f"chat:{session_id}", json.dumps(message))
            r.expire(f"chat:{session_id}", 86400)  # 24 hour TTL
            return {"status": "saved"}
        except Exception as e:
            return {"error": str(e)}
    
    def get_history(self, session_id: str, limit: int = 20) -> List[Dict]:
        try:
            r = redis.Redis(host=self.host, port=self.port, password=self.password, db=self.db)
            messages = r.lrange(f"chat:{session_id}", 0, limit - 1)
            return {"messages": [json.loads(m) for m in reversed(messages)]}
        except Exception as e:
            return {"error": str(e)}
    
    def clear_session(self, session_id: str):
        try:
            r = redis.Redis(host=self.host, port=self.port, password=self.password, db=self.db)
            r.delete(f"chat:{session_id}")
            return {"status": "cleared"}
        except Exception as e:
            return {"error": str(e)}
