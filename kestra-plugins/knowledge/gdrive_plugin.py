"""Google Drive Plugin"""
import os, json

class GoogleDrivePlugin:
    def __init__(self, credentials_path: str = None):
        self.credentials_path = credentials_path or os.environ.get('GOOGLE_CREDENTIALS_PATH')
    
    def list_files(self, folder_id: str = None, query: str = None):
        try:
            from googleapiclient.discovery import build
            from google.oauth2 import service_account
            
            creds = service_account.Credentials.from_service_account_file(self.credentials_path)
            service = build('drive', 'v3', credentials=creds)
            
            q = query or (f"'{folder_id}' in parents" if folder_id else None)
            results = service.files().list(q=q, fields="files(id, name, mimeType)").execute()
            return {"files": results.get('files', [])}
        except Exception as e:
            return {"error": str(e)}
    
    def download_file(self, file_id: str, output_path: str):
        try:
            from googleapiclient.discovery import build
            from google.oauth2 import service_account
            from googleapiclient.http import MediaIoBaseDownload
            import io
            
            creds = service_account.Credentials.from_service_account_file(self.credentials_path)
            service = build('drive', 'v3', credentials=creds)
            
            request = service.files().get_media(fileId=file_id)
            fh = io.FileIO(output_path, 'wb')
            downloader = MediaIoBaseDownload(fh, request)
            
            done = False
            while not done:
                status, done = downloader.next_chunk()
            
            return {"status": "downloaded", "path": output_path}
        except Exception as e:
            return {"error": str(e)}
