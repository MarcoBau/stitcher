package com.stitchit.sticher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StitchController {
    private static final String template = "Stitched  %s!";
    Logger LOG = LoggerFactory.getLogger(StitchController.class);

    @RequestMapping("/stitch/{objectId}")
    public String stitch(@PathVariable final String objectId) {
        LOG.info("Received stitching request {}", objectId);

        return String.format(template, objectId);
    }
}