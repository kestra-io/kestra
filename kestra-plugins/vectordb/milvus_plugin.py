"""Milvus Vector Database Plugin"""
import os, json

class MilvusPlugin:
    def __init__(self, host: str = "localhost", port: int = 19530):
        self.host = host
        self.port = port
    
    def insert(self, collection_name: str, entities: List[List]):
        try:
            from pymilvus import connections, Collection
            connections.connect(host=self.host, port=self.port)
            collection = Collection(collection_name)
            result = collection.insert(entities)
            return {"ids": result.primary_keys}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, collection_name: str, vectors: List[List[float]], limit: int = 5):
        try:
            from pymilvus import connections, Collection
            connections.connect(host=self.host, port=self.port)
            collection = Collection(collection_name)
            results = collection.search(vectors, "embedding", {"metric_type": "L2"}, limit=limit)
            return {"results": [[{"id": hit.id, "distance": hit.distance} for hit in hits] for hits in results]}
        except Exception as e:
            return {"error": str(e)}
