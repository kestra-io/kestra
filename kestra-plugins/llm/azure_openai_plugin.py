"""Azure OpenAI Plugin"""
import os, json

class AzureOpenAIPlugin:
    def __init__(self, endpoint: str = None, api_key: str = None):
        self.endpoint = endpoint or os.environ.get('AZURE_OPENAI_ENDPOINT')
        self.api_key = api_key or os.environ.get('AZURE_OPENAI_KEY')
    
    def chat(self, messages, deployment: str, temperature: float = 0.7):
        try:
            from openai import AzureOpenAI
            client = AzureOpenAI(azure_endpoint=self.endpoint, api_key=self.api_key, api_version="2024-02-01")
            response = client.chat.completions.create(model=deployment, messages=messages, temperature=temperature)
            return {"content": response.choices[0].message.content}
        except Exception as e:
            return {"error": str(e)}
