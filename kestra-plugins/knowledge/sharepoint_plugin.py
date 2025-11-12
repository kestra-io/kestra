"""SharePoint Plugin"""
import os, json

class SharePointPlugin:
    def __init__(self, site_url: str = None, client_id: str = None, client_secret: str = None):
        self.site_url = site_url or os.environ.get('SHAREPOINT_SITE_URL')
        self.client_id = client_id or os.environ.get('SHAREPOINT_CLIENT_ID')
        self.client_secret = client_secret or os.environ.get('SHAREPOINT_CLIENT_SECRET')
    
    def list_files(self, folder_path: str = ""):
        try:
            from office365.sharepoint.client_context import ClientContext
            from office365.runtime.auth.client_credential import ClientCredential
            
            credentials = ClientCredential(self.client_id, self.client_secret)
            ctx = ClientContext(self.site_url).with_credentials(credentials)
            
            folder = ctx.web.get_folder_by_server_relative_url(folder_path)
            files = folder.files
            ctx.load(files)
            ctx.execute_query()
            
            return {"files": [{"name": f.properties['Name'], "url": f.properties['ServerRelativeUrl']} for f in files]}
        except Exception as e:
            return {"error": str(e)}
    
    def download_file(self, file_url: str, output_path: str):
        try:
            from office365.sharepoint.client_context import ClientContext
            from office365.runtime.auth.client_credential import ClientCredential
            
            credentials = ClientCredential(self.client_id, self.client_secret)
            ctx = ClientContext(self.site_url).with_credentials(credentials)
            
            with open(output_path, "wb") as local_file:
                file = ctx.web.get_file_by_server_relative_url(file_url)
                file.download(local_file).execute_query()
            
            return {"status": "downloaded", "path": output_path}
        except Exception as e:
            return {"error": str(e)}
