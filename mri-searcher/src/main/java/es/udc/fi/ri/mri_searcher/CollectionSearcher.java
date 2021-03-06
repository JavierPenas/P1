package es.udc.fi.ri.mri_searcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class CollectionSearcher {
	
	private static InputStreamReader openDoc(File file) throws FileNotFoundException{
		FileInputStream fis = null;
		fis = new FileInputStream(file);
		
		if(fis==null)
			return null;
		else
			return new InputStreamReader(fis, StandardCharsets.UTF_8);
	}
	
	private static StringBuffer readFile(File file) throws Exception{
		InputStreamReader fis;
		fis = openDoc(file);
		BufferedReader buffer = new BufferedReader(fis);
        StringBuffer filecontent = new StringBuffer();
        String text = null;
        
        while((text=buffer.readLine()) != null){
      	  filecontent.append(text).append("\n");
        }
        return new StringBuffer(filecontent);
	}
	
	public static CharArraySet parseStopwords(File file) throws Exception{
		StringBuffer fileContent = readFile(file);
		String text = fileContent.toString();
		String[] lines = text.split("\n");
		
		CharArraySet charSet = new CharArraySet(lines.length,true);	
		
		for (int i = 0; i < lines.length; ++i) {
			charSet.add(lines[i]);
			//System.out.println(lines[i]);
		}
		
		Iterator iter = StandardAnalyzer.STOP_WORDS_SET.iterator();
		while(iter.hasNext()) {
		    char[] stopWord = (char[]) iter.next();
		    String s = (new String(stopWord));
		    charSet.add(s);
		    //System.out.println(s);
		}
		
		return charSet;
		
	}
	

	public static void main (String args[]) throws Exception{
		//INSTRUCCIONES DE USO
		String usage = "mri_searcher Usage: "
                 + " [-index INDEX_PATH] [-coll DOC_PATH] [-openmode create||append||create_or_append ] [-indexingmodel default||jm lmbda||dir mu]\n\n"
                 + "This indexes the documents in DOC_PATH, creating a Lucene index"
                 + "in INDEX_PATH following the indexing model specified";
			
		//VARIABLES DE INDEXACION
		String openmode = "create"; //OPENMODE
		String indexPath = null; //PATH DONDE SE CONSTRUIRA INDICE
		Map<String, Float> indexModel = new HashMap<String, Float>(); //MODELO INDEXACION
		List<String> docsPaths = new ArrayList<String>(); //ARRAY CON DIRECTORIOS A INDEXAR
		
		//VARIABLES BUSQUEDA, RELEVANCE FEEDBACK, y PS RELEVANCE FEEDBACK
		String indexin; 
		List<String> fieldsproc = new ArrayList<String>();
		List<String> fieldsvisual = new ArrayList<String>();
		Map<String, Float> searchModel = new HashMap<String, Float>(); //MODELO BUSQUEDA
		int cut = 0;
		int top = 0;
		int tq = 0;
		int td = 0;
		int ndr = 0;
		int ini = 1;
		int fin = -1;
		int nd = 0;
		int nw = 0;
		int rf1 = 0;
		int rf2 = 0;
		int prfjm = 0;
		int prfdir = 0;
		int explain = 0;
		
		for(int i=0;i<args.length;i++) {
			if("-openmode".equals(args[i])){
				if ((args[i+1]).equals("create")||(args[i+1]).equals("append")||
						(args[i+1]).equals("create_or_append")){
					openmode = args[i+1];
					i++;
				} else {
					//SI NO COINCIDE CON NINGUN OPENMODE ERROR
					System.out.println("openmode is create, append or create_or_append");
					System.out.println("exiting");
					System.exit(1);
				}	
			}else if ("-index".equals(args[i])){
				indexPath = args[i+1];
				i++;
			}else if("-indexin".equals(args[i])){
				indexin = args[i+1];
				i++;
			}else if ("-coll".equals(args[i])) {
				docsPaths.add(args[i+1]);
		        i++;
		    }else if("-search".equals(args[i])){
		    	if ((args[i+1]).equals("default")||(args[i+1]).equals("jm")||(args[i+1]).equals("dir")){
		    		if(indexModel.get(args[i+1])!=null){
		    			searchModel.put(args[i+1], indexModel.get(args[i+1]));
		    			i++;
		    		}else{
		    			System.out.println("search model must be the same in indexing and searching");
						System.out.println("exiting");
						System.exit(1);
		    		}
		    	}
		    }else if("-indexingmodel".equals(args[i])){
		    	if ((args[i+1]).equals("jm")||(args[i+1]).equals("dir")){
		    		indexModel.put(args[i+1], Float.parseFloat(args[i+2]));
					i+=2;
				} else if ((args[i+1]).equals("default")){
					indexModel.put(args[i+1], (float) 0.0);
					i++;
				}else{
					//SI NO COINCIDE CON NINGUN OPENMODE ERROR
					System.out.println("openmode is default, jm or dir");
					System.out.println("exiting");
					System.exit(1);
				}	
		    }else if("-cut".equals(args[i])){
		    	cut= Integer.parseInt(args[i+1]);
		    	i++;
		    }else if("-top".equals(args[i])){
		    	top= Integer.parseInt(args[i+1]);
		    	i++;
		    }else if("-queries".equals(args[i])){
		    	Pattern p = Pattern.compile("\\d++-\\d++");
		    	Pattern p2 = Pattern.compile("\\d++");
		    	if("all".equals(args[i+1])){
		    		//Es por defecto, no hacemos nada
		    	}else if(p.matcher(args[i+1]).matches()){
		    		ini = Integer.parseInt(args[i+1].substring(0, args[i+1].indexOf("-")));
		    		fin = Integer.parseInt(args[i+1].substring(args[i+1].indexOf("-")+1));
		    	}else if(p2.matcher(args[i+1]).matches()){
		    		ini = Integer.parseInt(args[i+1]);
		    		fin = Integer.parseInt(args[i+1]);
		    	}else{
		    		System.out.println("only valid all|int1|int1-int2 for -queries parameter");
					System.out.println("exiting");
					System.exit(1);
		    	}
		    	i++;
		    }else if("-fieldsproc".equals(args[i])){
		    	i++;
		    	while(args[i].equals("T")||args[i].equals("W")){
		    		fieldsproc.add(args[i]);
		    		i++;
		    	}
		    	i--;
		    }else if("-fieldsvisual".equals(args[i])){
		    	i++;
		    	while(args[i].equals("I")||args[i].equals("T")||args[i].equals("W")||args[i].equals("B")||args[i].equals("A")){
		    		fieldsvisual.add(args[i]);
		    		i++;
		    	}
		    	i--;
		    }else if("-rf1".equals(args[i])){
		    	 rf1 = 1;
		    	 tq = Integer.parseInt(args[i+1]);
		    	 td = Integer.parseInt(args[i+2]);
		    	 ndr = Integer.parseInt(args[i+3]);
		    	 i+= 3;
		    }else if("-rf2".equals(args[i])){
		    	rf2 = 1;
		    	ndr = Integer.parseInt(args[i+1]);
		    	i++;
		    }else if("-prfjm".equals(args[i])){
		    	prfjm = 1;
		    	nd = Integer.parseInt(args[i+1]);
		    	nw = Integer.parseInt(args[i+2]);
		    	i+=2;
		    }else if("-prfdir".equals(args[i])){
		    	prfdir = 1;
		    	nd = Integer.parseInt(args[i+1]);
		    	nw = Integer.parseInt(args[i+2]);
		    	i+=2;
		    }else if("-explain".equals(args[i])){
		    	explain = 1;
		    	i++;
		    }
	}
		
	//INDEXACION DE DOCUMENTOS
		 Date start = new Date();
		 System.out.println("Indexing ...");
		 CollectionIndexer.index(indexModel,indexPath,docsPaths,openmode);
		 Date end = new Date();
		 System.out.println("Indexing time : "+ (end.getTime() - start.getTime()) + " milliseconds");
		
		//PROCESADO DE ARCHIVO QUERIES Y QRELS
		String queriesPath = (docsPaths.get(0)+"\\cran.qry");
		String qrelsPath = (docsPaths.get(0)+"\\cranqrel");
		List<QuerY> queries = null;
		try {
			System.out.println("Processing QRELS from: "+qrelsPath);
			System.out.println("Processing Queries from: "+queriesPath);
			queries = CranQueryParser.parseDocument(queriesPath, qrelsPath);
			System.out.println("Processed!");			
		} catch (Exception e) {
			System.out.println("Error while parsing qrels and queries archives");
			System.exit(1);
		}
		
		//COMPUTAMOS METRICAS CON LAS QUERIES EN EL INDICE CREADO "indexPath"
		
		//CREAMOS UN READER PARA EL INDICE
		Directory dir = null;
		DirectoryReader indexReader = null;
		IndexSearcher indexSearcher = null;
		
		try{
			dir = FSDirectory.open(Paths.get(indexPath));
			indexReader = DirectoryReader.open(dir);
			//CREAMOS UN SEARCHER PARA EL INDICE
			indexSearcher = new IndexSearcher(indexReader);
			if(searchModel.get("default")!=null){
				indexSearcher.setSimilarity(new BM25Similarity());
			}else if(searchModel.get("jm")!=null){
				indexSearcher.setSimilarity(new LMJelinekMercerSimilarity(indexModel.get("jm")));
			}else if(searchModel.get("dir")!=null){
				indexSearcher.setSimilarity(new LMDirichletSimilarity(indexModel.get("dir")));
			}
		}catch(IOException e){
			
		}
		File file = new File("C:\\Users\\Javier\\Desktop\\RI2\\common_words");
		Analyzer analyzer = new StandardAnalyzer();
		String [] fields = fieldsproc.toArray(new String[0]);
		
		//Clausula aplicable a la busqueda (OR,AND,NOT)
				BooleanClause.Occur[] operator = new BooleanClause.Occur[fields.length];
				for(int i=0;i<fields.length;i++){
					operator[i]=BooleanClause.Occur.SHOULD;
				}
				    
				
		MultiFieldQueryParser queryParser = new MultiFieldQueryParser(fields, analyzer);
		//PARA CADA QUERY HACEMOS BUSQUEDA Y PROCESAMOS RESULTADOS
		
		if(fin ==-1){
			fin = queries.size();
		}
		float sumaAve = 0;
		for(int j= ini; j<=fin; j++ ){
			try {
				QuerY q = queries.get(j-1);
				//OBTENEMOS LOS RESULTADOS DE LA QUERY
				String queryText = QueryParser.escape(q.getText());
				String [] queryArray = new String[fieldsproc.size()];
				for(int i= 0; i<fieldsproc.size(); i++){
					queryArray[i] = queryText;
				}
				
				Query query = MultiFieldQueryParser.parse(queryArray, fields, operator, analyzer);
				q.setQuery(query);
				//System.out.println(query.toString());
				TopDocs topDocs = indexSearcher.search(query,indexReader.numDocs());
				ScoreDoc [] hits = topDocs.scoreDocs;
				q.setHits(hits);
				System.out.println("QUERY: "+q.getId());
				System.out.println(query.toString());
				topN(hits, fieldsvisual,indexSearcher, cut,q);
				q.setP10(Metrics.p10(hits, q,indexSearcher));
				q.setP20(Metrics.p20(hits, q, indexSearcher));
				q.setR10(Metrics.recall10(hits, q, indexSearcher));
				q.setR20(Metrics.recall20(hits, q, indexSearcher));
				sumaAve += Metrics.aveP(hits, q, indexSearcher);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//CALCULO DE MAP
		System.out.println("MAP:  "+sumaAve/fin);

		if(rf1==1){
			rf1(explain,ini, fin, tq, td, ndr, cut, sumaAve, indexReader, analyzer, fieldsproc, fieldsvisual, indexSearcher, queries);	
		}
		if(rf2==1){
			rf2(ini, fin, ndr, cut, sumaAve, indexReader, analyzer, fieldsproc, fieldsvisual, indexSearcher, queries);	
		}
		if(prfjm==1){
			prfjm(explain,0,ini,fin,queries, fieldsproc, indexSearcher, indexReader, analyzer, operator, fields, queryParser, nd, nw, searchModel.get("jm"));	
		}
		if(prfdir==1){
			prfjm(explain,1,ini, fin, queries, fieldsproc, indexSearcher, indexReader, analyzer, operator, fields, queryParser, nd, nw, searchModel.get("dir"));
		}
		
		
		
}
	private static void prfjm(int explain, int funcion,int ini,int fin,List<QuerY> queries,List<String> fieldsproc, IndexSearcher searcher,IndexReader reader,Analyzer analyzer,BooleanClause.Occur[] operator,String [] campos,MultiFieldQueryParser queryParser, int nd,int nw,float alfa) throws IOException, ParseException{
		float sumaAve = 0;
		float sumaAveExpandida = 0;
		System.out.println("PSEUDO RELEVANCE FEEDBACK");
		for(int j= ini; j<=fin; j++ ){
			double pqd = 0;
			Map<String,Palabra> pwr = new HashMap<String, Palabra>(); //MAP CON TODAS LAS PALABRAS DE LA COLECCION Y SU P(w|R) 
			QuerY q = queries.get(j-1);
			
			
			//OBTENEMOS LOS ND PRIMEROS DOCUMENTOS OBTENIDOS CON LA QUERY
			PostingsEnum postings = null;
			ScoreDoc[] hits = new ScoreDoc[nd];
			double pd = (1.0/nd);
			System.out.println(q.getHits().length);
			
			for(int i= 0; i<nd; i++){
				
				List<Palabra> palabrasQ = new ArrayList<Palabra>(); //PALABRAS DE LA QUERY Y SU FRECUENCIA EN DOCUMENTO i
				Map<String,Integer> terms = new HashMap<String, Integer>(); // PALABRAS DE UN DOCUMENTO Y SU FRECUENCIA EN i
				List<Palabra> palabras = new ArrayList<Palabra>(); //PALABRAS DE UN DOCUMENTO Y FRECUENCIA TOTAL + FRECUENCIA DOC i
				
				pqd = 0;
				hits[i] = q.getHits()[i];
				ScoreDoc score = hits[i];
				Document doc = searcher.doc(score.doc);
				List<IndexableField> fields = doc.getFields();
				int doc_size = 0; // NUMERO DE PALABRAS DEL DOCUMENTO
				int collection_size  = 0; //NUMERO DE PALABRAS EN COLECCION
				
				for(IndexableField f: fields){
					doc_size += f.stringValue().length(); //SUMAMOS NUMERO PALABRAS EN FIELD A TAMANO DOCUMENTO
					collection_size += reader.getSumTotalTermFreq(f.name()); //SUMAMOS NUMERO PALABRAS EN FIELD A COLECCION
					
					Terms terminos = reader.getTermVector(score.doc, f.name()); //OBTENEMOS TODOS LOS TERMINOS DEL FIELD
					if(terminos!=null){	
						TermsEnum termsEnum = null;
						termsEnum = terminos.iterator();
						while(termsEnum.next()!=null){		//RECORREMOS TODOS LOS TERMINO				
							if(!terms.containsKey(termsEnum.term().utf8ToString())){ //SI ESA PALABRA AUN NO ESTA ANADIDA
								postings = termsEnum.postings(postings, PostingsEnum.FREQS);
								postings.nextDoc();
								double docFreq = postings.freq(); //NUMERO OCURRENCIAS DEL TERMINO EN DOCUMENTO
								double totalTermFreq = termsEnum.totalTermFreq(); //NUMERO TOTAL OCURRENCIAS TERMINO EN COLECCION													
								terms.put(termsEnum.term().utf8ToString(), postings.freq()); //EN TERMS TENEMOS PAR (NOMBRE,FRECUENCIA EN DOC)
								palabras.add(new Palabra(termsEnum.term().utf8ToString(), totalTermFreq, docFreq));
							}
						}
					}
				}
				
				//CALCULO DE P(Q|D)
				
				StringTokenizer st = new StringTokenizer(q.getText());
				while(st.hasMoreTokens()){
					String p = st.nextToken();
					double doc_freq = 0.0;
					if(terms.containsKey(p)){
						 doc_freq = terms.get(p);
					}
					Term termino = new Term("W", p);
					palabrasQ.add(new Palabra(p, reader.totalTermFreq(termino), doc_freq));  //EN PALABRAS Q QUEDAN TODAS LAS PALABRAS DE QUERY Y FRECUENCIAS
				}
				
				//CALCULO P(Q|D)
				for(Palabra palabra : palabrasQ){
					if(funcion==0){
						if(palabra.getTotalTermFreq()!=0.0){
							 pqd += Math.log10(((1-alfa)*(palabra.getDocFreq()/(double) doc_size)) + (alfa*palabra.getTotalTermFreq()/(double) collection_size));	
						}
					}else if(funcion ==1){
						if(palabra.getTotalTermFreq()!=0.0){
							pqd += Math.log10( (palabra.getDocFreq()+(alfa*palabra.getTotalTermFreq()/(double) collection_size)) / ((double) doc_size + alfa));		
						}
					}
				}
				
				
				//EJECUTAR P(W|D)*P(Q|D)*P(D)
				
				for(Palabra palabra : palabras){
					double pwd = 0;
					if(funcion==0){
						 pwd = Math.log10(((1-alfa)*(palabra.getDocFreq()/(double) doc_size)) + (alfa*palabra.getTotalTermFreq()/(double) collection_size)); 				
					}else if(funcion==1){
						 pwd = Math.log10( (palabra.getDocFreq()+(alfa*palabra.getTotalTermFreq()/(double) collection_size)) / ((double) doc_size + alfa));
					}
					double sumPwr = pwd*pqd*pd;
					
					palabra.setPwd(pwd);
					//CREACION DE OBJETO PALABRADOC
					PalabraDoc pDoc = new PalabraDoc(pd, pwd, pqd);
					
					if(pwr.containsKey(palabra.getPalabra())){
						 Palabra p = pwr.get(palabra.getPalabra());
						 p.setPwr(p.getPwr()+sumPwr);
						 p.addPalabra(pDoc);
					     pwr.put(palabra.getPalabra(), p);
					}else{
						 palabra.setPwr(sumPwr);
						 palabra.addPalabra(pDoc);
						 pwr.put(palabra.getPalabra(), palabra);
					}
				}
			}
			
			//EN PWR TENEMOS UN STRING ASOCIADO A PALABRA QUE TIENE EL PWR TOTAL
			
			List<Entry<String,Palabra>> orderedTerms = Utilities.orderForMax(pwr);
			java.util.Iterator<Entry<String, Palabra>> it = orderedTerms.iterator();
			int i=1;
			Entry<String,Palabra> entry;
			StringBuffer sb = new StringBuffer(q.getText());
			while( i<=nw){
				try{
					entry=it.next();
					sb.append(" "+entry.getKey());
					if(explain ==1){
						System.out.println("----- EXPLAIN ------");
						System.out.println("PALABRA: "+entry.getKey());
						System.out.println("P(w|R)= "+entry.getValue().getPwr());
						for(PalabraDoc p : entry.getValue().getPalabras()){
							System.out.print("P(D)= "+p.getPd());
							System.out.print(" P(w|D)= "+p.getPwd());
							System.out.println(" P(Q|D)= "+ p.getPqd());
						}
						
					}
					i++;
				}catch(NoSuchElementException e){
					break;
				}
			}
			
			String queryText = QueryParser.escape(sb.toString());
			String [] queryArray = new String[fieldsproc.size()];
			for(int k= 0; k<fieldsproc.size(); k++){
				queryArray[k] = queryText;
			}
			
			Query query = MultiFieldQueryParser.parse(queryArray, campos, operator, analyzer);
			q.setQuery(query);
			System.out.println("QUERY ORIGINAL: "+q.getText());
			System.out.println("METRICAS ACTUALES: ");
			Metrics.p10(q.getHits(), q,searcher);
			Metrics.p20(q.getHits(), q, searcher);
			Metrics.recall10(q.getHits(), q, searcher);
			Metrics.recall20(q.getHits(), q, searcher);
			
			TopDocs topDocs = searcher.search(query,reader.numDocs());
			ScoreDoc [] resultados = topDocs.scoreDocs;
			ScoreDoc [] hitsExpandida = topDocs.scoreDocs;
			System.out.println("QUERY EXPANDIDA: "+query.toString());
			Metrics.p10(hitsExpandida, q,searcher);
			Metrics.p20(hitsExpandida, q, searcher);
			Metrics.recall10(hitsExpandida, q, searcher);
			Metrics.recall20(hitsExpandida, q, searcher);
			
			sumaAve += Metrics.aveP(q.getHits(), q, searcher);
			sumaAveExpandida += Metrics.aveP(hitsExpandida, q, searcher);
		}
		System.out.println("MAP ORIGINAL: "+sumaAve/fin);
		System.out.println("MAP EXPANDIDA: "+sumaAveExpandida/fin);
	}
	
	
	private static void rf1 (int explain,int ini, int fin,int tq,int td,int ndr,int cut, float sumaAve,IndexReader indexReader,Analyzer analyzer,List<String> fieldsproc,List<String> fieldsvisual,IndexSearcher indexSearcher,  List<QuerY> queries){
		float sumaAve2 = 0;
		//RELEVANCE FEEDBACK Rf1
		System.out.println("RELEVANCE FEEDBACK RF1");
		for(int j= ini; j<=fin; j++ ){
			try {
				QuerY q = queries.get(j-1);
				//OBTENEMOS LOS RESULTADOS DE LA QUERY
				RelevanceFeedback.rf1( explain,tq,  td,  ndr,  q,  fieldsproc,  indexReader,  analyzer );
				
				Query query = q.getQueryExpandida();
				
				//System.out.println(query.toString());
				TopDocs topDocs = indexSearcher.search(query,indexReader.numDocs());
				ScoreDoc [] hits = topDocs.scoreDocs;
				System.out.println("QUERY ID: "+q.getId());
				System.out.println("QUERY ANTERIOR: "+q.getQuery().toString());
				System.out.println("QUERY ACTUAL: "+query.toString());
				
				topN(hits, fieldsvisual,indexSearcher, cut,q);
				System.out.println("METRICAS ACTUALES: ");
				Metrics.p10(hits, q,indexSearcher);
				Metrics.p20(hits, q, indexSearcher);
				Metrics.recall10(hits, q, indexSearcher);
				Metrics.recall20(hits, q, indexSearcher);
				sumaAve2 += Metrics.aveP(hits, q, indexSearcher);
				
				System.out.println("METRICAS ANTERIORES: ");
				System.out.println("P@10: "+q.getP10()+"\nP@20: "+q.getP20()+"\nRecall@10: "+q.getR10()+"\nRecall@20: "+q.getR20());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//CALCULO DE MAP
		System.out.println("MAP ACTUAL:  "+sumaAve2/fin);
		System.out.println("MAP ANTERIOR:  "+sumaAve/fin);
		
	}

	private static void rf2(int ini, int fin,int ndr,int cut, float sumaAve,IndexReader indexReader,Analyzer analyzer,List<String> fieldsproc,List<String> fieldsvisual,IndexSearcher indexSearcher,  List<QuerY> queries) throws ParseException{
		float sumaAve2 = 0;
		System.out.println("RELEVANCE FEEDBACK RF2");
		for(int j= ini; j<=fin; j++ ){
			try {
				QuerY q = queries.get(j-1);
				//OBTENEMOS LOS RESULTADOS DE LA QUERY
			
				
				Query query = 	RelevanceFeedback.rf2(ndr, q, indexReader, fieldsproc, analyzer);
				
				//System.out.println(query.toString());
				TopDocs topDocs = indexSearcher.search(query,indexReader.numDocs());
				ScoreDoc [] hits = topDocs.scoreDocs;
				System.out.println("QUERY ID: "+q.getId());
				System.out.println("QUERY ANTERIOR: "+q.getQuery().toString());
				System.out.println("QUERY ACTUAL: "+query.toString());
				
				topN(hits, fieldsvisual,indexSearcher, cut,q);
				System.out.println("METRICAS ACTUALES: ");
				Metrics.p10(hits, q,indexSearcher);
				Metrics.p20(hits, q, indexSearcher);
				Metrics.recall10(hits, q, indexSearcher);
				Metrics.recall20(hits, q, indexSearcher);
				sumaAve2 += Metrics.aveP(hits, q, indexSearcher);
				
				System.out.println("METRICAS ANTERIORES: ");
				System.out.println("P@10: "+q.getP10()+"\nP@20: "+q.getP20()+"\nRecall@10: "+q.getR10()+"\nRecall@20: "+q.getR20());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//CALCULO DE MAP
		System.out.println("MAP ACTUAL:  "+sumaAve2/fin);
		System.out.println("MAP ANTERIOR:  "+sumaAve/fin);
	}
	
	private static void topN(ScoreDoc [] hits,List<String> fieldsvisual,IndexSearcher searcher, int cut,QuerY q) throws IOException{
		for(int i= 0; i<cut ; i++){
			ScoreDoc score = hits[i];
			Document doc = searcher.doc(score.doc);
			System.out.println("SCORE: "+score.score);
			if (!(q.getRelevants()==null)){
				int idDoc = Integer.parseInt(doc.get("I"));
				System.out.println("RELEVANT : "+q.getRelevants().contains(idDoc) );
			} else{
				System.out.println("RELEVANT : ");
			}
			
			for(String s: fieldsvisual){
				System.out.println(doc.get(s));
			}
		}
	}
	


}