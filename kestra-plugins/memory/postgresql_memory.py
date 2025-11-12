"""PostgreSQL Memory Store Plugin for Conversational AI"""
import os, json, psycopg2
from typing import List, Dict

class PostgreSQLMemory:
    def __init__(self, connection_string: str = None):
        self.connection_string = connection_string or os.environ.get('POSTGRES_CONNECTION_STRING')
    
    def save_message(self, session_id: str, role: str, content: str, metadata: Dict = None):
        try:
            conn = psycopg2.connect(self.connection_string)
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO chat_messages (session_id, role, content, metadata, created_at)
                VALUES (%s, %s, %s, %s, NOW())
            """, (session_id, role, content, json.dumps(metadata or {})))
            conn.commit()
            cursor.close()
            conn.close()
            return {"status": "saved"}
        except Exception as e:
            return {"error": str(e)}
    
    def get_history(self, session_id: str, limit: int = 20) -> List[Dict]:
        try:
            conn = psycopg2.connect(self.connection_string)
            cursor = conn.cursor()
            cursor.execute("""
                SELECT role, content, metadata, created_at
                FROM chat_messages
                WHERE session_id = %s
                ORDER BY created_at DESC
                LIMIT %s
            """, (session_id, limit))
            messages = [{"role": row[0], "content": row[1], "metadata": json.loads(row[2]), "timestamp": str(row[3])} for row in cursor.fetchall()]
            cursor.close()
            conn.close()
            return {"messages": list(reversed(messages))}
        except Exception as e:
            return {"error": str(e)}
    
    def clear_session(self, session_id: str):
        try:
            conn = psycopg2.connect(self.connection_string)
            cursor = conn.cursor()
            cursor.execute("DELETE FROM chat_messages WHERE session_id = %s", (session_id,))
            conn.commit()
            cursor.close()
            conn.close()
            return {"status": "cleared"}
        except Exception as e:
            return {"error": str(e)}
