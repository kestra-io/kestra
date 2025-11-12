"""Mistral AI Plugin"""
import os, json
from typing import List, Dict, Any

class MistralPlugin:
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.environ.get('MISTRAL_API_KEY')
    
    def chat(self, messages: List[Dict], model: str = "mistral-large-latest") -> Dict[str, Any]:
        try:
            from mistralai.client import MistralClient
            client = MistralClient(api_key=self.api_key)
            response = client.chat(model=model, messages=messages)
            return {"content": response.choices[0].message.content}
        except Exception as e:
            return {"error": str(e)}
