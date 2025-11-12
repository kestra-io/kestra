"""Google Gemini Plugin"""
import os, json

class GeminiPlugin:
    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.environ.get('GOOGLE_API_KEY')
    
    def generate(self, prompt: str, model: str = "gemini-pro"):
        try:
            import google.generativeai as genai
            genai.configure(api_key=self.api_key)
            model_instance = genai.GenerativeModel(model)
            response = model_instance.generate_content(prompt)
            return {"content": response.text}
        except Exception as e:
            return {"error": str(e)}
