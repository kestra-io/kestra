"""Elasticsearch Vector Search Plugin"""
import os, json

class ElasticsearchPlugin:
    def __init__(self, hosts: List[str] = ["localhost:9200"], api_key: str = None):
        self.hosts = hosts
        self.api_key = api_key
    
    def index_document(self, index: str, document: Dict, id: str = None):
        try:
            from elasticsearch import Elasticsearch
            es = Elasticsearch(self.hosts, api_key=self.api_key)
            result = es.index(index=index, document=document, id=id)
            return {"result": result['result'], "id": result['_id']}
        except Exception as e:
            return {"error": str(e)}
    
    def vector_search(self, index: str, query_vector: List[float], k: int = 5, field: str = "embedding"):
        try:
            from elasticsearch import Elasticsearch
            es = Elasticsearch(self.hosts, api_key=self.api_key)
            query = {"knn": {"field": field, "query_vector": query_vector, "k": k, "num_candidates": 100}}
            results = es.search(index=index, knn=query)
            return {"hits": [{"id": hit['_id'], "score": hit['_score'], "source": hit['_source']} for hit in results['hits']['hits']]}
        except Exception as e:
            return {"error": str(e)}
