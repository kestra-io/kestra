"""Weaviate Vector Database Plugin"""
import os, json

class WeaviatePlugin:
    def __init__(self, url: str = None, api_key: str = None):
        self.url = url or os.environ.get('WEAVIATE_URL')
        self.api_key = api_key or os.environ.get('WEAVIATE_API_KEY')
    
    def create(self, class_name: str, properties: Dict, vector: List[float]):
        try:
            import weaviate
            client = weaviate.Client(url=self.url, auth_client_secret=weaviate.AuthApiKey(self.api_key))
            result = client.data_object.create(properties, class_name, vector=vector)
            return {"uuid": result, "status": "success"}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, class_name: str, vector: List[float], limit: int = 5):
        try:
            import weaviate
            client = weaviate.Client(url=self.url, auth_client_secret=weaviate.AuthApiKey(self.api_key))
            result = client.query.get(class_name).with_near_vector({"vector": vector}).with_limit(limit).do()
            return {"results": result}
        except Exception as e:
            return {"error": str(e)}
