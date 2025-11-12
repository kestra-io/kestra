"""AWS DynamoDB Memory Store Plugin"""
import os, json
from typing import List, Dict
from datetime import datetime

class DynamoDBMemory:
    def __init__(self, table_name: str = None, region: str = "us-east-1"):
        self.table_name = table_name or os.environ.get('DYNAMODB_TABLE_NAME')
        self.region = region
    
    def save_message(self, session_id: str, role: str, content: str):
        try:
            import boto3
            dynamodb = boto3.resource('dynamodb', region_name=self.region)
            table = dynamodb.Table(self.table_name)
            
            table.put_item(Item={
                'session_id': session_id,
                'timestamp': datetime.now().isoformat(),
                'role': role,
                'content': content
            })
            return {"status": "saved"}
        except Exception as e:
            return {"error": str(e)}
    
    def get_history(self, session_id: str, limit: int = 20) -> List[Dict]:
        try:
            import boto3
            from boto3.dynamodb.conditions import Key
            
            dynamodb = boto3.resource('dynamodb', region_name=self.region)
            table = dynamodb.Table(self.table_name)
            
            response = table.query(
                KeyConditionExpression=Key('session_id').eq(session_id),
                Limit=limit,
                ScanIndexForward=False
            )
            return {"messages": list(reversed(response['Items']))}
        except Exception as e:
            return {"error": str(e)}
    
    def clear_session(self, session_id: str):
        try:
            import boto3
            from boto3.dynamodb.conditions import Key
            
            dynamodb = boto3.resource('dynamodb', region_name=self.region)
            table = dynamodb.Table(self.table_name)
            
            response = table.query(KeyConditionExpression=Key('session_id').eq(session_id))
            with table.batch_writer() as batch:
                for item in response['Items']:
                    batch.delete_item(Key={'session_id': session_id, 'timestamp': item['timestamp']})
            
            return {"status": "cleared"}
        except Exception as e:
            return {"error": str(e)}
