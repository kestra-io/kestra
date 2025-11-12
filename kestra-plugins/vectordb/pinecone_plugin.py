"""Pinecone Vector Database Plugin"""
import os, json
from typing import List, Dict, Any, Optional

class PineconePlugin:
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.environ.get('PINECONE_API_KEY')
    
    def upsert(self, index_name: str, vectors: List[Dict], namespace: str = ""):
        try:
            from pinecone import Pinecone
            pc = Pinecone(api_key=self.api_key)
            index = pc.Index(index_name)
            index.upsert(vectors=vectors, namespace=namespace)
            return {"status": "success", "count": len(vectors)}
        except Exception as e:
            return {"error": str(e)}
    
    def query(self, index_name: str, vector: List[float], top_k: int = 5, namespace: str = "", include_metadata: bool = True):
        try:
            from pinecone import Pinecone
            pc = Pinecone(api_key=self.api_key)
            index = pc.Index(index_name)
            results = index.query(vector=vector, top_k=top_k, namespace=namespace, include_metadata=include_metadata)
            return {"matches": [{"id": m.id, "score": m.score, "metadata": m.metadata} for m in results.matches]}
        except Exception as e:
            return {"error": str(e)}
    
    def delete(self, index_name: str, ids: List[str], namespace: str = ""):
        try:
            from pinecone import Pinecone
            pc = Pinecone(api_key=self.api_key)
            index = pc.Index(index_name)
            index.delete(ids=ids, namespace=namespace)
            return {"status": "success"}
        except Exception as e:
            return {"error": str(e)}
