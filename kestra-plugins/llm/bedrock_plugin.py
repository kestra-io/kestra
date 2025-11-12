"""AWS Bedrock Plugin"""
import os, json

class BedrockPlugin:
    def __init__(self, region: str = "us-east-1"):
        self.region = region
    
    def invoke(self, model_id: str, prompt: str):
        try:
            import boto3
            client = boto3.client('bedrock-runtime', region_name=self.region)
            response = client.invoke_model(
                modelId=model_id,
                body=json.dumps({"prompt": prompt, "max_tokens": 512})
            )
            return json.loads(response['body'].read())
        except Exception as e:
            return {"error": str(e)}
