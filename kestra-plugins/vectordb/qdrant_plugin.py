"""Qdrant Vector Database Plugin"""
import os, json

class QdrantPlugin:
    def __init__(self, url: str = "localhost", port: int = 6333, api_key: str = None):
        self.url = url
        self.port = port
        self.api_key = api_key
    
    def upsert(self, collection_name: str, points: List[Dict]):
        try:
            from qdrant_client import QdrantClient
            from qdrant_client.models import PointStruct
            client = QdrantClient(url=self.url, port=self.port, api_key=self.api_key)
            client.upsert(collection_name=collection_name, points=[PointStruct(**p) for p in points])
            return {"status": "success"}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, collection_name: str, query_vector: List[float], limit: int = 5):
        try:
            from qdrant_client import QdrantClient
            client = QdrantClient(url=self.url, port=self.port, api_key=self.api_key)
            results = client.search(collection_name=collection_name, query_vector=query_vector, limit=limit)
            return {"results": [{"id": r.id, "score": r.score, "payload": r.payload} for r in results]}
        except Exception as e:
            return {"error": str(e)}
