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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;

@RestController
public class StitchController {
    private static final String STITCHED_DIRECTORY = "_stitched/";
    Logger LOG = LoggerFactory.getLogger(StitchController.class);

    private void checkStitchedDirerctory() {
        final File theDir = new File(STITCHED_DIRECTORY);
        // if the directory does not exist, create it
        if (!theDir.exists()) {
            try {
                theDir.mkdir();
            } catch (final SecurityException se) {
                // handle it
            }
        }
    }

    @RequestMapping(value = "/stitch/{oauth}/{folder}")
    public @ResponseBody String save(@PathVariable("oauth") final String oauth, @PathVariable("folder") final String folder) {
        LOG.info("Received stitching request {}/{}", oauth, folder);

        String result;

        final DbxRequestConfig config = new DbxRequestConfig("tz764utcclnkf06", Locale.getDefault().toString());
        final DbxClient client = new DbxClient(config, oauth);
        try {
            checkStitchedDirerctory();

            LOG.info("Linked account: " + client.getAccountInfo().displayName);
            final DbxEntry.WithChildren listing = client.getMetadataWithChildren("/" + folder);
            System.out.println("Files in the root path:");
            final PDFMergerUtility merger = new PDFMergerUtility();
            for (final DbxEntry child : listing.children) {
                System.out.println("    " + child.name + ": " + child.toString());
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final DbxEntry.File downloadedFile = client.getFile("/" + folder + "/" + child.asFile().name, null, outputStream);
                System.out.println("Metadata: " + downloadedFile.toString());
                merger.addSource(new ByteArrayInputStream(outputStream.toByteArray()));
                outputStream.close();
            }

            final String outputName = STITCHED_DIRECTORY + folder + ".pdf";

            final FileOutputStream mergedOutputStream = new FileOutputStream(outputName);
            merger.setDestinationStream(mergedOutputStream);
            merger.mergeDocuments();

            final File inputFile = new File(outputName);
            final FileInputStream inputStream = new FileInputStream(inputFile);
            try {
                final DbxEntry.File uploadedFile = client.uploadFile("/" + outputName, DbxWriteMode.add(), inputFile.length(), inputStream);
                client.createShareableUrl(uploadedFile.path);
                System.out.println("Uploaded: " + client.createShareableUrl(uploadedFile.path));
                result = client.createShareableUrl(uploadedFile.path);// String.format(template, client.getAccountInfo().displayName);
            } finally {
                inputStream.close();
                inputFile.delete();
            }

        } catch (final Exception e) {
            result = "Something went wrong";
            LOG.error(e.getMessage(), e);
        }
        return result;
    }
}