package org.apache.lucene.codec;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.bool.BooleanDocValuesFormat;
import org.apache.lucene.codecs.lucene80.Lucene80Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
@Fork(value = 1)
@State(Scope.Benchmark)
public class IndexingBooleanDocValuesBench {

    private static final String TEMPORARY_PATH = "/tmp/bench";

    @Param({ "10", "35", "50", "60", "90" })
    private int density = 90;

    //@Param({ "1", "2", "3", "4", "5", "10" })
    private int numSegments = 1;

    @Param({ "dense", "sparse" })
    private String compression;

    @Param({ "false", "true" })
    private boolean useBooleanCodec;

    private final int maxDoc = 2_000_000;
    private String store;

    private Random random = new Random();
    private IndexWriter writer;

    public static void main(String[] args) throws Exception {

        Options options = new OptionsBuilder().include(IndexingBooleanDocValuesBench.class.getName())
                                              .forks(2)
                                              .verbosity(VerboseMode.NORMAL)
                                              .resultFormat(ResultFormatType.JSON)
                                              .build();
        new Runner(options).run();
    }

    @Setup
    public void init() throws IOException {
        store = (useBooleanCodec ? "boolean_" : "") + "store_" + density;

        FSDirectory directory = FSDirectory.open(new File(TEMPORARY_PATH).toPath());
        IndexWriterConfig conf = new IndexWriterConfig(null).setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                                                            .setCodec(new BooleanCodec())
                                                            .setUseCompoundFile(false)
                                                            .setMaxBufferedDocs(500000)
                                                            .setRAMBufferSizeMB(1024);

        writer = new IndexWriter(directory, conf);
    }

    @Benchmark
    public int createIndex() throws IOException {
        int numTrueValues = 0;
        boolean checkSparseDv = "sparse".equals(compression);
        float probability = density / 100f;
        for (int i = 0; i < maxDoc; i++) {
            Document doc = new Document();
            doc.add(new StringField("id", String.valueOf(i), Field.Store.NO));

            if (random.nextFloat() < probability) {
                numTrueValues++;
                doc.add(new NumericDocValuesField(store, 1));
            } else if (!checkSparseDv || random.nextBoolean()) { //to check different compressions (Lucene80DocValuesProducer$SparseNumericDocValues vs Lucene80DocValuesProducer$DenseNumericDocValues)
                doc.add(new NumericDocValuesField(store, 0));
            }

            writer.addDocument(doc);
        }

        writer.forceMerge(numSegments);
        writer.commit();

        return numTrueValues;
    }

    /**
     * Simple example codec with ability to create {@link BooleanDocValuesFormat} as default implementation always
     * returns "Lucene80" format for any field.
     */
    public static class BooleanCodec extends Lucene80Codec {

        private final DocValuesFormat booleanDVFormat = DocValuesFormat.forName("Boolean");

        @Override
        public DocValuesFormat getDocValuesFormatForField(String field) {
            if (field.startsWith("boolean_")) {
                return booleanDVFormat;
            } else {
                return super.getDocValuesFormatForField(field);
            }
        }
    }
}