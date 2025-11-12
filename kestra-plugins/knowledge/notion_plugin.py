"""Notion Plugin"""
import os, json

class NotionPlugin:
    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.environ.get('NOTION_API_KEY')
    
    def query_database(self, database_id: str, filter_params: Dict = None):
        try:
            from notion_client import Client
            notion = Client(auth=self.api_key)
            results = notion.databases.query(database_id=database_id, filter=filter_params or {})
            return {"results": results['results']}
        except Exception as e:
            return {"error": str(e)}
    
    def get_page(self, page_id: str):
        try:
            from notion_client import Client
            notion = Client(auth=self.api_key)
            page = notion.pages.retrieve(page_id=page_id)
            blocks = notion.blocks.children.list(block_id=page_id)
            return {"page": page, "blocks": blocks['results']}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, query: str):
        try:
            from notion_client import Client
            notion = Client(auth=self.api_key)
            results = notion.search(query=query)
            return {"results": results['results']}
        except Exception as e:
            return {"error": str(e)}
