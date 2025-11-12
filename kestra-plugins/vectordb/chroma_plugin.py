"""ChromaDB Plugin"""
import os, json

class ChromaPlugin:
    def __init__(self, path: str = "./chroma_db"):
        self.path = path
    
    def add(self, collection_name: str, documents: List[str], metadatas: List[Dict], ids: List[str]):
        try:
            import chromadb
            client = chromadb.PersistentClient(path=self.path)
            collection = client.get_or_create_collection(collection_name)
            collection.add(documents=documents, metadatas=metadatas, ids=ids)
            return {"status": "success", "count": len(ids)}
        except Exception as e:
            return {"error": str(e)}
    
    def query(self, collection_name: str, query_texts: List[str], n_results: int = 5):
        try:
            import chromadb
            client = chromadb.PersistentClient(path=self.path)
            collection = client.get_or_create_collection(collection_name)
            results = collection.query(query_texts=query_texts, n_results=n_results)
            return {"results": results}
        except Exception as e:
            return {"error": str(e)}
