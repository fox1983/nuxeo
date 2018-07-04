package org.nuxeo.ecm.core.convert.tests;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.AbstractBlob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.cache.CachableBlobHolder;
import org.nuxeo.ecm.core.convert.cache.SimpleCachableBlobHolder;
import org.nuxeo.ecm.core.convert.extension.Converter;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DummyMultiOutputConverter implements Converter {

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        List<Blob> blobs = new ArrayList<>();
        blobs.add(new StringBlob("blob1","text/plain",AbstractBlob.UTF_8,"file1"));
        blobs.add(new StringBlob("blob2","text/plain",AbstractBlob.UTF_8,"file2"));
        return new SimpleCachableBlobHolder(blobs);
    }

    @Override
    public void init(ConverterDescriptor descriptor) {
    }


}
