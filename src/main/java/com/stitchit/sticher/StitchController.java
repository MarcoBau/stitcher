package com.stitchit.sticher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;

import org.apache.pdfbox.util.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;

@RestController
public class StitchController {
    private static final String template = "Stitched for %s!";
    Logger LOG = LoggerFactory.getLogger(StitchController.class);

    @RequestMapping(value = "/stitch", method = RequestMethod.POST)
    public @ResponseBody String save(@RequestBody final StitchRequest request) {
        LOG.info("Received stitching request {}", request);

        String result;

        final DbxRequestConfig config = new DbxRequestConfig("tz764utcclnkf06", Locale.getDefault().toString());
        final DbxClient client = new DbxClient(config, request.getOauth());
        try {
            LOG.info("Linked account: " + client.getAccountInfo().displayName);
            final DbxEntry.WithChildren listing = client.getMetadataWithChildren("/" + request.getFolder());
            System.out.println("Files in the root path:");
            final PDFMergerUtility merger = new PDFMergerUtility();
            for (final DbxEntry child : listing.children) {
                System.out.println("    " + child.name + ": " + child.toString());
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final DbxEntry.File downloadedFile = client.getFile("/" + request.getFolder() + "/" + child.asFile().name, null, outputStream);
                System.out.println("Metadata: " + downloadedFile.toString());
                merger.addSource(new ByteArrayInputStream(outputStream.toByteArray()));
                outputStream.close();
            }

            final String outputName = request.getFolder() + ".pdf";

            final FileOutputStream mergedOutputStream = new FileOutputStream(outputName);
            merger.setDestinationStream(mergedOutputStream);
            merger.mergeDocuments();

            final File inputFile = new File(outputName);
            final FileInputStream inputStream = new FileInputStream(inputFile);
            try {
                final DbxEntry.File uploadedFile = client.uploadFile("/" + outputName, DbxWriteMode.add(), inputFile.length(), inputStream);
                System.out.println("Uploaded: " + uploadedFile.toString());
            } finally {
                inputStream.close();
            }
            // final DbxEntry.File downloadedFile = client.getFile("/magnum-opus.txt", null, outputStream);
            // System.out.println("Metadata: " + downloadedFile.toString());
            //
            // final File inputFile = new File(outputName, outputName, false, 0, outputName, null, null, outputName);
            // final FileInputStream inputStream = new FileInputStream(inputFile);
            // try {
            // final DbxEntry.File uploadedFile = client.uploadFile("/magnum-opus.txt", DbxWriteMode.add(), inputFile.length(), inputStream);
            // System.out.println("Uploaded: " + uploadedFile.toString());
            // } finally {
            // outputStream.close();
            // // inputStream.close();
            // }
            result = String.format(template, client.getAccountInfo().displayName);
        } catch (final Exception e) {
            result = "Something went wrong";
            LOG.error(e.getMessage(), e);
        }
        return result;
    }
}