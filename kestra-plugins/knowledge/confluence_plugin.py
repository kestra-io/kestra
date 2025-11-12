"""Atlassian Confluence Plugin"""
import os, json
from typing import List, Dict, Optional

class ConfluencePlugin:
    def __init__(self, url: str = None, username: str = None, api_token: str = None):
        self.url = url or os.environ.get('CONFLUENCE_URL')
        self.username = username or os.environ.get('CONFLUENCE_USERNAME')
        self.api_token = api_token or os.environ.get('CONFLUENCE_API_TOKEN')
    
    def get_page(self, page_id: str) -> Dict:
        try:
            from atlassian import Confluence
            confluence = Confluence(url=self.url, username=self.username, password=self.api_token)
            page = confluence.get_page_by_id(page_id, expand='body.storage')
            return {"title": page['title'], "content": page['body']['storage']['value'], "id": page['id']}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, cql: str, limit: int = 10) -> Dict:
        try:
            from atlassian import Confluence
            confluence = Confluence(url=self.url, username=self.username, password=self.api_token)
            results = confluence.cql(cql, limit=limit)
            return {"results": [{"id": r['content']['id'], "title": r['content']['title']} for r in results['results']]}
        except Exception as e:
            return {"error": str(e)}
    
    def get_space_content(self, space_key: str) -> Dict:
        try:
            from atlassian import Confluence
            confluence = Confluence(url=self.url, username=self.username, password=self.api_token)
            pages = confluence.get_all_pages_from_space(space_key, expand='body.storage')
            return {"pages": [{"id": p['id'], "title": p['title'], "content": p.get('body', {}).get('storage', {}).get('value', '')} for p in pages]}
        except Exception as e:
            return {"error": str(e)}
