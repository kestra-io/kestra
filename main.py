from dotenv import load_dotenv
load_dotenv()
from fastapi import FastAPI
from pydantic import BaseModel
import openai
import os
import requests
import json

# Initialize FastAPI app
app = FastAPI()

# Define the structure for the request body
class Message(BaseModel):
    role: str
    content: str

# Route to interact with the OpenRouter API
@app.post("/chat/")
async def chat_with_openrouter(messages: list[Message]):
    # Prepare the messages for OpenRouter API
    formatted_messages = [{"role": msg.role, "content": msg.content} for msg in messages]

    try:
        # OpenRouter API call to get a response
        response = requests.post(
            url="https://openrouter.ai/api/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {os.getenv('OPENROUTER_API_KEY')}",
                "HTTP-Referer": "<YOUR_SITE_URL>",  # Optional: Replace with your site URL if necessary
                "X-Title": "<YOUR_SITE_NAME>",  # Optional: Replace with your site title if necessary
            },
            data=json.dumps({
                "model": "openai/gpt-4o",  # Specify the model to use
                "messages": formatted_messages
            })
        )

        # Check if the request was successful
        if response.status_code == 200:
            result = response.json()
            return {"response": result.get('choices')[0].get('message').get('content')}
        else:
            return {"error": f"Request failed with status code {response.status_code}"}

    except Exception as e:
        return {"error": str(e)}

# Route to verify the server is running
@app.get("/")
async def root():
    return {"message": "Welcome to the OpenAI FastAPI integration!"}
