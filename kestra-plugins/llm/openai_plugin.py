"""OpenAI LLM Plugin - GPT-4, GPT-3.5-turbo, Embeddings"""
from typing import List, Dict, Optional, Any
import os
import json

class OpenAIPlugin:
    """OpenAI integration plugin for Kestra workflows"""
    
    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.environ.get('OPENAI_API_KEY')
        
    def chat_completion(
        self,
        messages: List[Dict[str, str]],
        model: str = "gpt-4",
        temperature: float = 0.7,
        max_tokens: Optional[int] = None,
        stream: bool = False
    ) -> Dict[str, Any]:
        """Generate chat completion"""
        try:
            from openai import OpenAI
            client = OpenAI(api_key=self.api_key)
            
            response = client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
                stream=stream
            )
            
            if stream:
                return {"stream": response}
            
            return {
                "content": response.choices[0].message.content,
                "model": response.model,
                "usage": {
                    "prompt_tokens": response.usage.prompt_tokens,
                    "completion_tokens": response.usage.completion_tokens,
                    "total_tokens": response.usage.total_tokens
                },
                "finish_reason": response.choices[0].finish_reason
            }
        except Exception as e:
            return {"error": str(e), "status": "failed"}
    
    def embeddings(
        self,
        texts: List[str],
        model: str = "text-embedding-3-large"
    ) -> Dict[str, Any]:
        """Generate embeddings for texts"""
        try:
            from openai import OpenAI
            client = OpenAI(api_key=self.api_key)
            
            response = client.embeddings.create(
                input=texts,
                model=model
            )
            
            return {
                "embeddings": [item.embedding for item in response.data],
                "model": response.model,
                "usage": {
                    "prompt_tokens": response.usage.prompt_tokens,
                    "total_tokens": response.usage.total_tokens
                }
            }
        except Exception as e:
            return {"error": str(e), "status": "failed"}
    
    def function_calling(
        self,
        messages: List[Dict[str, str]],
        functions: List[Dict[str, Any]],
        model: str = "gpt-4"
    ) -> Dict[str, Any]:
        """Chat completion with function calling"""
        try:
            from openai import OpenAI
            client = OpenAI(api_key=self.api_key)
            
            response = client.chat.completions.create(
                model=model,
                messages=messages,
                functions=functions,
                function_call="auto"
            )
            
            return {
                "content": response.choices[0].message.content,
                "function_call": response.choices[0].message.function_call,
                "usage": {
                    "total_tokens": response.usage.total_tokens
                }
            }
        except Exception as e:
            return {"error": str(e), "status": "failed"}

if __name__ == "__main__":
    import sys
    plugin = OpenAIPlugin()
    
    if len(sys.argv) > 1:
        operation = sys.argv[1]
        
        if operation == "chat":
            result = plugin.chat_completion([
                {"role": "user", "content": "Hello!"}
            ])
            print(json.dumps(result, indent=2))
        elif operation == "embed":
            result = plugin.embeddings(["Sample text"])
            print(json.dumps(result, indent=2))
