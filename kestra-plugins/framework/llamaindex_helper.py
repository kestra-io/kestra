"""LlamaIndex Framework Helper Plugin"""
import os, json
from typing import List, Dict, Any, Optional

class LlamaIndexHelper:
    """Helper for LlamaIndex operations in Kestra workflows"""
    
    @staticmethod
    def create_index(
        documents: List[str],
        index_type: str = "vector",
        llm_provider: str = "openai",
        model: str = "gpt-4"
    ):
        """Create a LlamaIndex index from documents"""
        try:
            from llama_index.core import VectorStoreIndex, Document, Settings
            from llama_index.llms.openai import OpenAI
            from llama_index.embeddings.openai import OpenAIEmbedding
            
            # Configure LLM and embeddings
            Settings.llm = OpenAI(model=model, api_key=os.environ.get('OPENAI_API_KEY'))
            Settings.embed_model = OpenAIEmbedding(api_key=os.environ.get('OPENAI_API_KEY'))
            
            # Create documents
            docs = [Document(text=doc) for doc in documents]
            
            # Create index
            if index_type == "vector":
                index = VectorStoreIndex.from_documents(docs)
            
            return {"status": "index_created", "type": index_type, "doc_count": len(docs)}
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def query_index(
        query: str,
        index_path: str = None,
        response_mode: str = "compact"
    ):
        """Query a LlamaIndex index"""
        try:
            from llama_index.core import load_index_from_storage, StorageContext
            
            if index_path:
                storage_context = StorageContext.from_defaults(persist_dir=index_path)
                index = load_index_from_storage(storage_context)
            
            query_engine = index.as_query_engine(response_mode=response_mode)
            response = query_engine.query(query)
            
            return {
                "response": str(response),
                "source_nodes": [{"text": node.text, "score": node.score} for node in response.source_nodes]
            }
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def create_chat_engine(
        index_path: str = None,
        chat_mode: str = "context"
    ):
        """Create a chat engine from index"""
        try:
            from llama_index.core import load_index_from_storage, StorageContext
            
            if index_path:
                storage_context = StorageContext.from_defaults(persist_dir=index_path)
                index = load_index_from_storage(storage_context)
            
            chat_engine = index.as_chat_engine(chat_mode=chat_mode)
            
            return {"status": "chat_engine_created", "mode": chat_mode}
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def load_data_from_sources(
        source_type: str,
        config: Dict[str, Any]
    ):
        """Load data from various sources using LlamaIndex loaders"""
        try:
            documents = []
            
            if source_type == "web":
                from llama_index.readers.web import SimpleWebPageReader
                loader = SimpleWebPageReader()
                documents = loader.load_data(config.get('urls', []))
            
            elif source_type == "database":
                from llama_index.readers.database import DatabaseReader
                loader = DatabaseReader(
                    sql_database=config.get('connection_string')
                )
                documents = loader.load_data(query=config.get('query'))
            
            elif source_type == "notion":
                from llama_index.readers.notion import NotionPageReader
                loader = NotionPageReader(
                    integration_token=config.get('token')
                )
                documents = loader.load_data(page_ids=config.get('page_ids', []))
            
            return {
                "documents": [{"text": d.text, "metadata": d.metadata} for d in documents],
                "count": len(documents)
            }
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def create_knowledge_graph(
        documents: List[str],
        kg_type: str = "simple"
    ):
        """Create a knowledge graph index"""
        try:
            from llama_index.core import KnowledgeGraphIndex, Document
            
            docs = [Document(text=doc) for doc in documents]
            index = KnowledgeGraphIndex.from_documents(docs)
            
            return {"status": "knowledge_graph_created", "type": kg_type}
        except Exception as e:
            return {"error": str(e)}
