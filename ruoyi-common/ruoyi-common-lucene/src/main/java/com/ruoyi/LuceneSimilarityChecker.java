package com.ruoyi;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;
import java.io.StringReader;

public class LuceneSimilarityChecker {

    public static void main(String[] args) throws IOException, ParseException {
        // 1. 初始化分析器和索引写入器
        Analyzer analyzer = new SmartChineseAnalyzer();
        Directory directory = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter indexWriter = new IndexWriter(directory, config);

        // 2. 创建样本中文标题数据
        String[] titles = {
            "这是一个测试文档，包含了一些中文内容。",
            "这是另一个测试文档，也包含了一些中文内容。",
            "第三个文档，包含不同的中文内容。",
            "这是第二个测试文档，包含了一些中文内容",
            "这是第二个测试文档，包含了一些中文内容。"
        };

        // 3. 将标题创建索引
        for (int i = 0; i < titles.length; i++) {
            Document document = new Document();
            document.add(new StringField("id", String.valueOf(i), Field.Store.YES));
            document.add(new TextField("title", titles[i], Field.Store.YES));
            indexWriter.addDocument(document);
        }
        indexWriter.close();

        // 4. 创建索引搜索器
        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);

        // 5. 使用MoreLikeThis查找与输入文本相似的文档
        MoreLikeThis mlt = new MoreLikeThis(reader);
        mlt.setAnalyzer(analyzer);
        mlt.setFieldNames(new String[]{"title"});
        mlt.setMinTermFreq(1);   // 词频要求
        mlt.setMinDocFreq(1);    // 文档频率要求

        String inputTitle = "这是第二个测试文档，包含了一些中文内容。"; // 待检查相似度的标题
        Query query = mlt.like("title", new StringReader(inputTitle));

        // 6. 查找与输入文本相似的文档
        TopDocs topDocs = searcher.search(query, 10);
        System.out.println("相似标题:");
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            System.out.println("标题: " + doc.get("title") + " | 相似度得分: " + scoreDoc.score);
        }

        reader.close();
        directory.close();
    }
}

