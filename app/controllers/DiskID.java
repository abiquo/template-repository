package controllers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import models.OVFPackage;
import models.OVFPackage.DiskFormatType;
import models.OVFPackage.MemorySizeUnitType;

import org.apache.commons.io.FilenameUtils;

import play.Play;
import play.jobs.JobsPlugin;
import play.libs.WS;
import play.libs.WS.FileParam;
import play.libs.WS.HttpResponse;

import com.google.gson.JsonObject;

/** Guess virtual disk format */
public class DiskID
{

    static class DiskId
    {
        final DiskFormatType format;

        final long hdBytes;

        public DiskId(final DiskFormatType format, final long hdBytes)
        {
            this.format = format;
            this.hdBytes = hdBytes;
        }
    }

    public static Future<DiskId> useDiskId(final String url, final Long fileSize) throws IOException 
    {
        // XXX InputStream fis = WS.url(object.diskFilePath).get().getStream();
        URL diskUrl = new URL(url);
        InputStream fis = diskUrl.openStream();
        return useDiskId(headFile(fis), FilenameUtils.getExtension(diskUrl.getFile()), fileSize);
    }

    public static Future<DiskId> useDiskId(final File diskFile)
    {
        try
        {
            return useDiskId(headFile(new FileInputStream(diskFile)),
                FilenameUtils.getExtension(diskFile.getName()), diskFile.length());
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    
    private static Future<DiskId> useDiskId(final File chunckDiskFile, final String extension,final Long fileSize) 
    {
        return JobsPlugin.executor.submit(new Callable<DiskId>()
            {
                @Override
                public DiskId call() throws Exception
                {

                    HttpResponse resp =
                        WS.url(getDiskIdUrl()).files(new FileParam(chunckDiskFile, "chunk")) //
                        .post(); // XXX postAsync

                    if (resp.getStatus() != 200)
                    {
                        play.Logger.error("DiskId returns HTTP %d at %s", resp.getStatus(), getDiskIdUrl());
                        return new DiskId(DiskFormatType.UNKNOWN, 0);
                    }

                    final JsonObject diskId = resp.getJson().getAsJsonObject();
                    final Long hdBytes = getTemplateVirtualSizeInBytes(diskId, fileSize);
                    final boolean sameVirtualSize = fileSize >= hdBytes;
                    play.Logger.trace("sameVirtualSize "+sameVirtualSize);
                    
                    return new DiskId(getDiskFormatTypeFromDiskId(diskId, extension, sameVirtualSize), hdBytes);      
                }
            });
    }

    
    private static long getTemplateVirtualSizeInBytes(final JsonObject diskId,final Long fileSize)
    {
        try
        {
            final String virtual_size = diskId.get("virtual_size").getAsString();
            
            Double hd = Double.parseDouble(virtual_size.substring(0, virtual_size.length() - 1));
            MemorySizeUnitType unit =
                MemorySizeUnitType.valueOf(virtual_size.substring(virtual_size.length() - 1) + "B");
        
            return OVFPackage.hdInBytes(hd, unit);
        }
        catch (Exception e)
        {
            play.Logger.warn("Can't read the virtual disk size from DiskId, assuming flat");
            return fileSize;
        }
    }

    private static DiskFormatType getDiskFormatTypeFromDiskId(final JsonObject diskId,
        final String extension, final boolean sameVirtualSize)
    {
        String formatQemu = diskId.get("format").getAsString();
        
        if("vpc".equalsIgnoreCase(formatQemu))
        {
            return sameVirtualSize ? DiskFormatType.VHD_FLAT : DiskFormatType.VHD_SPARSE;
        }
        else if("vdi".equalsIgnoreCase(formatQemu))
        {
            return sameVirtualSize ? DiskFormatType.VDI_FLAT : DiskFormatType.VDI_SPARSE;
        }
        else if(formatQemu.startsWith("qcow")) //  qcow2
        {
            return sameVirtualSize ? DiskFormatType.QCOW2_FLAT: DiskFormatType.QCOW2_SPARSE;
        }
        else if("vmdk".equalsIgnoreCase(formatQemu))
        {
            String variant = "";
            try
            {
                variant = diskId.get("variant").getAsString();
            }
            catch (Exception e)
            {
            }
            // XXX not using variant (only streamOptimized), SPARSE detected by the disk file size
            return  "streamOptimized".equalsIgnoreCase(variant) ? DiskFormatType.VMDK_STREAM_OPTIMIZED : //
                sameVirtualSize ? DiskFormatType.VMDK_FLAT : DiskFormatType.VMDK_SPARSE;
        //monolithic
        }
        else if("raw".equalsIgnoreCase(formatQemu))
        {
            return extension.equalsIgnoreCase("vmdk") ? DiskFormatType.VMDK_FLAT : DiskFormatType.RAW;
        }
        else
        {
            play.Logger.error("UNKNOWN DiskId format %s", formatQemu);
            return DiskFormatType.UNKNOWN;
        }
    }

    private static String getDiskIdUrl()
    {
        return Play.configuration.getProperty("ovfcatalog.diskIdUrl",
            "http://diskid.frameos.org/?format=json");
    }

    /** -- Get the required file chunks -*- */

    private final static int REQUIRED_LINES = 20;

    private static File headFile(final InputStream inputstream)
    {
        File outFile = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        try
        {
            outFile = File.createTempFile(UUID.randomUUID().toString(), "head");
            out = new DataOutputStream(new FileOutputStream(outFile));
            in = new DataInputStream(inputstream);

            byte[] line = new byte[1024];

            for (int i = 0; i <= REQUIRED_LINES; i++)
            {
                in.readFully(line);
                out.write(line);
            }

        }
        catch (Exception e)
        {
            throw new RuntimeException("Can't read disk file header", e);
        }
        finally
        {
            try
            {
                out.close();
                in.close();
            }
            catch (Exception e)
            {
            }
        }

        return outFile;
    }
}
