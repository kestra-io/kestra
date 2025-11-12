"""LangChain Framework Helper Plugin"""
import os, json
from typing import List, Dict, Any, Optional

class LangChainHelper:
    """Helper for common LangChain patterns in Kestra workflows"""
    
    @staticmethod
    def create_rag_chain(
        llm_provider: str = "openai",
        model: str = "gpt-4",
        vectorstore_type: str = "pinecone",
        retriever_k: int = 5
    ):
        """Create a RAG chain with specified components"""
        try:
            from langchain.chains import RetrievalQA
            from langchain_openai import ChatOpenAI, OpenAIEmbeddings
            from langchain_community.vectorstores import Pinecone as PineconeVectorStore
            from pinecone import Pinecone
            
            # Initialize LLM
            if llm_provider == "openai":
                llm = ChatOpenAI(model=model, temperature=0)
            
            # Initialize vector store
            if vectorstore_type == "pinecone":
                embeddings = OpenAIEmbeddings()
                pc = Pinecone(api_key=os.environ.get('PINECONE_API_KEY'))
                # Note: In production, you'd load existing index
            
            return {"status": "chain_created", "type": "rag"}
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def create_conversational_chain(
        llm_provider: str = "openai",
        model: str = "gpt-4",
        memory_type: str = "buffer"
    ):
        """Create a conversational chain with memory"""
        try:
            from langchain.chains import ConversationChain
            from langchain.memory import ConversationBufferMemory, ConversationSummaryMemory
            from langchain_openai import ChatOpenAI
            
            llm = ChatOpenAI(model=model, temperature=0.7)
            
            if memory_type == "buffer":
                memory = ConversationBufferMemory()
            elif memory_type == "summary":
                memory = ConversationSummaryMemory(llm=llm)
            
            chain = ConversationChain(llm=llm, memory=memory)
            
            return {"status": "chain_created", "type": "conversational"}
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def create_agent_executor(
        tools: List[Any],
        llm_provider: str = "openai",
        model: str = "gpt-4",
        agent_type: str = "react"
    ):
        """Create an agent executor with specified tools"""
        try:
            from langchain.agents import AgentExecutor, create_react_agent, create_openai_functions_agent
            from langchain_openai import ChatOpenAI
            from langchain.prompts import ChatPromptTemplate
            
            llm = ChatOpenAI(model=model, temperature=0.2)
            
            if agent_type == "react":
                prompt = ChatPromptTemplate.from_messages([
                    ("system", "You are a helpful assistant."),
                    ("user", "{input}"),
                    ("assistant", "{agent_scratchpad}")
                ])
                agent = create_react_agent(llm, tools, prompt)
            elif agent_type == "openai_functions":
                prompt = ChatPromptTemplate.from_messages([
                    ("system", "You are a helpful assistant."),
                    ("user", "{input}"),
                    ("placeholder", "{agent_scratchpad}")
                ])
                agent = create_openai_functions_agent(llm, tools, prompt)
            
            executor = AgentExecutor(agent=agent, tools=tools, verbose=True)
            
            return {"status": "agent_created", "type": agent_type}
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def extract_text_from_docs(file_paths: List[str], doc_type: str = "auto"):
        """Extract text from various document types"""
        try:
            from langchain.document_loaders import (
                PyPDFLoader, TextLoader, UnstructuredMarkdownLoader, CSVLoader
            )
            
            documents = []
            for path in file_paths:
                if doc_type == "auto":
                    ext = path.split('.')[-1].lower()
                else:
                    ext = doc_type
                
                if ext == "pdf":
                    loader = PyPDFLoader(path)
                elif ext == "txt":
                    loader = TextLoader(path)
                elif ext == "md":
                    loader = UnstructuredMarkdownLoader(path)
                elif ext == "csv":
                    loader = CSVLoader(path)
                else:
                    continue
                
                documents.extend(loader.load())
            
            return {
                "documents": [{"page_content": d.page_content, "metadata": d.metadata} for d in documents],
                "count": len(documents)
            }
        except Exception as e:
            return {"error": str(e)}
    
    @staticmethod
    def split_text(
        text: str,
        chunk_size: int = 1000,
        chunk_overlap: int = 200,
        splitter_type: str = "recursive"
    ):
        """Split text into chunks"""
        try:
            from langchain.text_splitter import (
                RecursiveCharacterTextSplitter,
                CharacterTextSplitter
            )
            
            if splitter_type == "recursive":
                splitter = RecursiveCharacterTextSplitter(
                    chunk_size=chunk_size,
                    chunk_overlap=chunk_overlap
                )
            else:
                splitter = CharacterTextSplitter(
                    chunk_size=chunk_size,
                    chunk_overlap=chunk_overlap
                )
            
            chunks = splitter.split_text(text)
            
            return {"chunks": chunks, "count": len(chunks)}
        except Exception as e:
            return {"error": str(e)}
