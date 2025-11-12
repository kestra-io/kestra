"""FAISS Vector Search Plugin"""
import os, json, numpy as np

class FAISSPlugin:
    def __init__(self, dimension: int = 768):
        self.dimension = dimension
        self.index = None
    
    def create_index(self, index_type: str = "flat"):
        try:
            import faiss
            if index_type == "flat":
                self.index = faiss.IndexFlatL2(self.dimension)
            elif index_type == "ivf":
                quantizer = faiss.IndexFlatL2(self.dimension)
                self.index = faiss.IndexIVFFlat(quantizer, self.dimension, 100)
            return {"status": "created", "type": index_type}
        except Exception as e:
            return {"error": str(e)}
    
    def add(self, vectors: List[List[float]]):
        try:
            import faiss
            vectors_np = np.array(vectors).astype('float32')
            self.index.add(vectors_np)
            return {"status": "success", "count": len(vectors)}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, query_vector: List[float], k: int = 5):
        try:
            query_np = np.array([query_vector]).astype('float32')
            distances, indices = self.index.search(query_np, k)
            return {"distances": distances[0].tolist(), "indices": indices[0].tolist()}
        except Exception as e:
            return {"error": str(e)}
