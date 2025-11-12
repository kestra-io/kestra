"""Airtable Plugin"""
import os, json

class AirtablePlugin:
    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.environ.get('AIRTABLE_API_KEY')
    
    def list_records(self, base_id: str, table_name: str):
        try:
            from pyairtable import Api
            api = Api(self.api_key)
            table = api.table(base_id, table_name)
            records = table.all()
            return {"records": [{"id": r['id'], "fields": r['fields']} for r in records]}
        except Exception as e:
            return {"error": str(e)}
    
    def create_record(self, base_id: str, table_name: str, fields: Dict):
        try:
            from pyairtable import Api
            api = Api(self.api_key)
            table = api.table(base_id, table_name)
            record = table.create(fields)
            return {"id": record['id'], "fields": record['fields']}
        except Exception as e:
            return {"error": str(e)}
    
    def search(self, base_id: str, table_name: str, formula: str):
        try:
            from pyairtable import Api
            api = Api(self.api_key)
            table = api.table(base_id, table_name)
            records = table.all(formula=formula)
            return {"records": [{"id": r['id'], "fields": r['fields']} for r in records]}
        except Exception as e:
            return {"error": str(e)}
