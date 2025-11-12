"""HuggingFace Plugin"""
import os, json
from typing import Dict, Any

class HuggingFacePlugin:
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.environ.get('HUGGINGFACE_API_KEY')
    
    def inference(self, model: str, inputs: str) -> Dict[str, Any]:
        try:
            from huggingface_hub import InferenceClient
            client = InferenceClient(token=self.api_key)
            response = client.text_generation(inputs, model=model)
            return {"content": response}
        except Exception as e:
            return {"error": str(e)}
