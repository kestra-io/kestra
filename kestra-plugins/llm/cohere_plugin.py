"""Cohere LLM Plugin"""
import os, json
from typing import List, Dict, Optional, Any

class CoherePlugin:
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.environ.get('COHERE_API_KEY')
    
    def chat(self, message: str, model: str = "command") -> Dict[str, Any]:
        try:
            import cohere
            co = cohere.Client(self.api_key)
            response = co.chat(message=message, model=model)
            return {"content": response.text, "model": model}
        except Exception as e:
            return {"error": str(e)}
    
    def embed(self, texts: List[str], model: str = "embed-english-v3.0") -> Dict[str, Any]:
        try:
            import cohere
            co = cohere.Client(self.api_key)
            response = co.embed(texts=texts, model=model, input_type="search_document")
            return {"embeddings": response.embeddings, "model": model}
        except Exception as e:
            return {"error": str(e)}
