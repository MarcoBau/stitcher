package com.stitchit.sticher;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxEntry.WithChildren;
import com.dropbox.core.DbxException;
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

    @RequestMapping(value = "/stitch/{oauth}/{folder}/{order}")
    public @ResponseBody String mergeDocuments(@PathVariable("oauth") final String oauth, @PathVariable("folder") final String folder,
            @PathVariable("order") final String order) {
        LOG.info("Received stitching request {}/{}/{}", oauth, folder, order);

        String result = null;

        final DbxClient client = setupDbxClient(oauth);
        try {
            checkStitchedDirerctory();

            LOG.info("Linked account: " + client.getAccountInfo().displayName);
            final DbxEntry.WithChildren listing = client.getMetadataWithChildren("/" + folder);
            final String[] fileOrder = order.split(",");

            result = mergePdf(folder, client, listing, fileOrder);
            // FIXME the current pptx merge implementation is not working correctly
            // result = mergePptx(folder, client, listing, fileOrder);

        } catch (

        final Exception e)

        {
            result = "Something went wrong";
            LOG.error(e.getMessage(), e);
        }
        return result;

    }

    private String mergePdf(final String folder, final DbxClient client, final DbxEntry.WithChildren listing, final String[] fileOrder)
            throws DbxException, IOException, FileNotFoundException, COSVisitorException {
        String result = "";
        final PDFMergerUtility merger = new PDFMergerUtility();

        for (final String fileName : fileOrder) {
            System.out.println("Files:" + fileName);
            for (final DbxEntry child : listing.children) {
                if (fileName.equals(child.name) && child.name.endsWith(".pdf")) {
                    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    final DbxEntry.File downloadedFile = client.getFile("/" + folder + "/" + child.asFile().name, null, outputStream);
                    System.out.println(child.name + ": " + child.toString());
                    System.out.println("   Metadata: " + downloadedFile.toString());

                    merger.addSource(new ByteArrayInputStream(outputStream.toByteArray()));
                    outputStream.close();
                    break;
                }
            }
        }
        final String outputName = STITCHED_DIRECTORY + folder + ".pdf";

        final FileOutputStream mergedOutputStream = new FileOutputStream(outputName);
        merger.setDestinationStream(mergedOutputStream);
        merger.mergeDocuments();

        final File inputFile = new File(outputName);
        final FileInputStream inputStream = new FileInputStream(inputFile);
        try {
            if (inputFile.length() > 0) {
                final DbxEntry.File uploadedFile = client.uploadFile("/" + outputName, DbxWriteMode.force(), inputFile.length(), inputStream);
                final String shareableUrl = client.createShareableUrl(uploadedFile.path);
                System.out.println("Uploaded: " + shareableUrl);
                result = shareableUrl;
            }
        } finally {
            inputStream.close();
            inputFile.delete();
        }
        return result;
    }

    private String mergePptx(final String folder, final DbxClient client, final WithChildren listing, final String[] fileOrder)
            throws DbxException, IOException, COSVisitorException {
        String result = "";
        final XMLSlideShow pptx = new XMLSlideShow();

        for (final String fileName : fileOrder) {
            System.out.println("Files:" + fileName);
            for (final DbxEntry child : listing.children) {
                if (fileName.equals(child.name) && child.name.endsWith(".pptx")) {
                    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    // final DbxEntry.File downloadedFile = client.getFile("/" + folder + "/" + child.asFile().name, null, outputStream);
                    System.out.println(child.name + ": " + child.toString());

                    final XMLSlideShow srcPptx = new XMLSlideShow(new ByteArrayInputStream(outputStream.toByteArray()));

                    for (final XSLFSlide srcSlide : srcPptx.getSlides()) {

                        // merging the contents
                        try {
                            pptx.createSlide().importContent(srcSlide);
                        } catch (final NullPointerException | IllegalArgumentException e) {
                            LOG.warn("Something is not ok with de pptx ", e.getMessage());
                        }
                    }
                    srcPptx.close();
                    outputStream.close();
                    break;
                }
            }
        }
        final String outputName = STITCHED_DIRECTORY + folder + ".pptx";
        final FileOutputStream mergedOutputStream = new FileOutputStream(outputName);

        // saving the changes to a file
        pptx.write(mergedOutputStream);

        final File inputFile = new File(outputName);
        final FileInputStream inputStream = new FileInputStream(inputFile);
        try {
            if (inputFile.length() > 24234) {// empty pptx
                final DbxEntry.File uploadedFile = client.uploadFile("/" + outputName, DbxWriteMode.force(), inputFile.length(), inputStream);
                final String shareableUrl = client.createShareableUrl(uploadedFile.path);
                System.out.println("Uploaded: " + shareableUrl);
                result = shareableUrl;
            }
        } finally {
            pptx.close();
            inputStream.close();
            inputFile.delete();
        }
        return result;
    }

    @RequestMapping(value = "/")
    public @ResponseBody String root() {
        return "<h1>Nothing to see. This is just a cloud sewing machine!</h1>";
    }

    private DbxClient setupDbxClient(final String oauth) {
        final DbxRequestConfig config = new DbxRequestConfig("tz764utcclnkf06", Locale.getDefault().toString());
        final DbxClient client = new DbxClient(config, oauth);
        return client;
    }

    @RequestMapping(value = "/submit/{oauth}/{folder}/{fileName}", method = RequestMethod.POST)
    public @ResponseBody String submit(@PathVariable("oauth") final String oauth, @PathVariable("folder") final String folder,
            @PathVariable("fileName") final String fileName, @RequestParam("file") final MultipartFile file) {
        LOG.info("Received submit request {}/{}/{}", oauth, folder, fileName);
        if (!file.isEmpty()) {
            try {
                final DbxClient client = setupDbxClient(oauth);

                final byte[] bytes = file.getBytes();
                final BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(new File(fileName)));
                stream.write(bytes);
                stream.close();

                final File inputFile = new File(fileName);
                final FileInputStream inputStream = new FileInputStream(inputFile);
                try {
                    final DbxEntry.File uploadedFile = client.uploadFile("/" + folder + "/" + fileName, DbxWriteMode.force(), inputFile.length(), inputStream);
                    final String shareableUrl = client.createShareableUrl(uploadedFile.path);
                    System.out.println("Uploaded: " + shareableUrl);
                } finally {
                    inputStream.close();
                    inputFile.delete();
                }

                return "You successfully uploaded " + fileName + "!";
            } catch (final Exception e) {
                return "You failed to upload " + fileName + " => " + e.getMessage();
            }
        } else {
            return "You failed to upload " + fileName + " because the file was empty.";
        }
    }
}