package controllers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;

import models.OVFPackage;
import models.OVFPackage.DiskFormatType;
import models.OVFPackage.MemorySizeUnitType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import play.Play;
import play.i18n.Messages;
import play.libs.OpenID;
import play.libs.OpenID.UserInfo;
import play.libs.WS;
import play.libs.WS.FileParam;
import play.libs.WS.HttpResponse;
import play.mvc.Before;

import com.google.gson.JsonObject;

public class OVFPackages extends CRUD
{

    public static void ovfindex()
    {
        List<OVFPackage> ovfpackages = OVFPackage.findAll();

        request.format = "xml";
        render(ovfpackages);
    }

    final static DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendMonthOfYear(2).appendLiteral('-')//
        .appendDayOfMonth(2).appendLiteral('-')//
        .appendMillisOfDay(8).toFormatter();

    
    
    private static void createTemplateFromDiskUrl(final OVFPackage object)
    {
        final String timestamp = new DateTime().toString(TIME_FORMATTER);

        String absPath = null;

        try
        {
            // remove extension from default name (file name)
            object.name = URLEncoder.encode(FilenameUtils.getBaseName(object.name), "UTF-8");
            // TODO also in save

            // check name is unique or append time-stamp
            if (OVFPackage.find("name", object.name).first() != null)
            {
                object.name = object.name + timestamp;
            }

            URL diskUrl = new URL(object.diskFilePath);

            try
            {                
                object.diskFileSize = Long.valueOf(WS.url(object.diskFilePath).head().getHeader("Content-Length"));
            }
            catch (Exception e) 
            {
                response.status = 404;
                renderText("Url not found "+object.diskFilePath);
            }
                
//            InputStream fis = WS.url(object.diskFilePath).get().getStream();
            InputStream fis = diskUrl.openStream();
            DiskId diskId = useDiskId(headFile(fis));

            object.diskFileFormat = diskId.type;
            object.hd = (long) diskId.hd; // FIXME double round
            object.hdSizeUnit = object.hdSizeUnit;
            object.hdInBytes = OVFPackage.hdInBytes(object.hd, object.hdSizeUnit);

            play.Logger.info("DiskID guess %s\t %d\t %s", object.diskFileFormat.name(), object.hd,
                object.hdSizeUnit.name());

            validation.valid(object);
            if (validation.hasErrors())
            {
                play.Logger.error("Can't create template %s\t %s\t %s\n%s", object.id, object.name,
                    object.diskFilePath, validation.errorsMap().toString());
                response.status = 500;
                renderText("Can't validate Template attributes"+validation.errorsMap().toString());
                return;

            }
            object._save();

            // object = object.save();
            play.Logger.info("Template %s\t %s\t %s", object.id, object.name, object.diskFilePath);
        }
        catch (Exception e)
        {
            play.Logger.error(e, "Template %s FAIL ", object.name);
            e.printStackTrace();

            object._delete();
            try
            {
                new File(absPath).delete();
            }
            catch (Exception ed)
            {
            }

            response.status = 500;
            renderText(e.getMessage());
        }
    }
    
    private static String urlSelected(final String url)
    {
        if(url.startsWith("http://"))
        {
            return url;
        }
        else if(url.contains("/"))
        {
            return "http://"+url;
        }
        else
        {
            return null;
        }
    }
    
    public static void createOvf(final OVFPackage object, final File diskFile)
    {

        final String timestamp = new DateTime().toString(TIME_FORMATTER);

        object.user = session.get("username");

        String url = urlSelected(object.diskFilePath);
        if(url!=null)
        {
            object.diskFilePath = url;
            createTemplateFromDiskUrl(object);
            return;
        }
        
        
        String absPath = null;

        try
        {
            // remove extension from default name (file name)
            object.name = URLEncoder.encode(FilenameUtils.getBaseName(object.name), "UTF-8");
            // TODO also in save

            // check name is unique or append time-stamp
            if (OVFPackage.find("name", object.name).first() != null)
            {
                object.name = object.name + timestamp;
            }

            DiskId diskId = useDiskId(diskFile);

            object.diskFileFormat = diskId.type;
            object.hd = (long) diskId.hd; // FIXME double round
            object.hdSizeUnit = object.hdSizeUnit;
            object.hdInBytes = OVFPackage.hdInBytes(object.hd, object.hdSizeUnit);

            play.Logger.info("DiskID guess %s\t %d\t %s", object.diskFileFormat.name(), object.hd,
                object.hdSizeUnit.name());

            object.diskFilePath = diskFile.getName();

            absPath = FilenameUtils.concat(getRepositoryLocation(), object.diskFilePath);
            if (new File(absPath).exists())
            {
                object.diskFilePath =
                    FilenameUtils.concat(FilenameUtils.getFullPath(object.diskFilePath),
                        FilenameUtils.getBaseName(object.diskFilePath) + timestamp + '.'
                            + FilenameUtils.getExtension(object.diskFilePath));

                absPath = FilenameUtils.concat(getRepositoryLocation(), object.diskFilePath);

                // response.status = 409;// conflict
                // renderText("Disk file already exist at " + diskFilePath);
                // return;
            }

            validation.valid(object);
            if (validation.hasErrors())
            {
                play.Logger.error("Can't create template %s\t %s\t %s\n%s", object.id, object.name,
                    object.diskFilePath, validation.errorsMap().toString());
                response.status = 500;
                renderText("Can't validate Template attributes");
                return;

            }
            object._save();

            // object = object.save();
            play.Logger.info("Template %s\t %s\t %s", object.id, object.name, object.diskFilePath);

            FileUtils.copyFile(diskFile, new File(absPath));
        }
        catch (Exception e)
        {
            play.Logger.error(e, "Template %s FAIL ", object.name);
            e.printStackTrace();

            object._delete();
            try
            {
                new File(absPath).delete();
            }
            catch (Exception ed)
            {
            }

            response.status = 500;
            renderText(e.getMessage());
        }
    }

    /** ********** DELETE ********** */

    public static void delete(final String id)
    {
        OVFPackage object = OVFPackage.findById(Long.valueOf(id));
        notFoundIfNull(object);

        if(!object.isDiskUrl())
        {
            try
            {
                File diskFile = new File(getRepositoryLocation() + object.diskFilePath);
                diskFile.delete();

            }
            catch (Exception e)
            {
                flash.error(Messages.get("crud.delete.error", "OVFPackage"));
                redirect(request.controller + ".show", object._key());
            }            
        }
        
        try
        {
            object._delete();
        }
        catch (Exception e)
        {
            flash.error(Messages.get("crud.delete.error", "OVFPackage"));
            redirect(request.controller + ".show", object._key());
        }
        flash.success(Messages.get("crud.deleted", "OVFPackage"));
        redirect(request.controller + ".list");
    }

    /** ********** GET ********** */

    // public static void get(final Long id)
    // {
    // OVFPackage ovfpackage = OVFPackage.findById(id);
    // notFoundIfNull(ovfpackage);
    //
    // render(ovfpackage);
    // }

    public static void getByName(final String name)
    {
        OVFPackage ovfpackage = (OVFPackage) OVFPackage.find("byName", name).first();
        notFoundIfNull(ovfpackage);

        renderTemplate("OVFPackages/get.xml", ovfpackage);
        // get(ovfpackage.id);
    }

    public static void getRepository(final String diskFilePath)
    {        
//        if(diskFilePath.startsWith("http://"))
//        {
//            redirect(diskFilePath);
//        }
//        
        File diskFile = new File(FilenameUtils.concat(getRepositoryLocation(), diskFilePath));

        play.Logger.info("file : %s", diskFilePath);

        renderBinary(diskFile);
    }

    private static String getRepositoryLocation()
    {
        String path = Play.configuration.getProperty("ovfcatalog.repositoryPath");

        if (!new File(path).exists())
        {
            throw new RuntimeException("repository folder do not exist " + path);
        }

        return path.endsWith("/") ? path : path.concat("/");
    }

    /** ********** DISK ID guess virtual disk format ********** */

    static class DiskId
    {
        DiskFormatType type;

        double hd;

        MemorySizeUnitType unit;

        public DiskId(final DiskFormatType type, final double hd, final MemorySizeUnitType unit)
        {
            super();
            this.type = type;
            this.hd = hd;
            this.unit = unit;
        }
    }

    private static DiskId useDiskId(final File diskFile) throws Exception
    {

        HttpResponse resp =
            WS.url(getDiskIdUrl()).files(new FileParam(headFile(new FileInputStream(diskFile)), "chunk")).post();

        if (resp.getStatus() != 200)
        {
            // TODO show error
            throw new RuntimeException("Can't use " + getDiskIdUrl()
                + " to guess the template format");
        }

        JsonObject diskId = resp.getJson().getAsJsonObject();
        
        String format = diskId.get("format").getAsString();
        String variant = "no_variant";
        try
        {
            variant = diskId.get("variant").getAsString();
            
            variant = variant.replaceAll("streamOptimized", "stream_Optimized");
            variant = variant.replaceAll("monolithic", "");
        }
        catch (Exception e)
        {
        }

        DiskFormatType type =
            format.equalsIgnoreCase("vmdk") ? DiskFormatType.valueOf((format + "_" + variant)
                .toUpperCase()) : format.equalsIgnoreCase("qcow2") ? DiskFormatType.QCOW2_SPARSE
                : DiskFormatType.valueOf(format.toUpperCase());

        // SIZE
        Double hd = 1.0;
        MemorySizeUnitType unit = MemorySizeUnitType.BYTE;
        try
        {
            final String virtual_size = diskId.get("virtual_size").getAsString();
            hd = Double.parseDouble(virtual_size.substring(0, virtual_size.length() - 1));
            unit =
                MemorySizeUnitType.valueOf(virtual_size.substring(virtual_size.length() - 1) + "B");
        }
        catch (Exception e)
        {
        }

        return new DiskId(type, hd, unit);

    }

    private final static String DISK_ID_URL_DEFAULT = "http://diskid.frameos.org/?format=json";

    private static String getDiskIdUrl()
    {
        return Play.configuration.getProperty("ovfcatalog.diskIdUrl", DISK_ID_URL_DEFAULT);
    }

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

    // /
    @Before(unless = {"login", "authenticate", "ovfindex", "getByName", "getRepository"})
    static void checkAuthenticated()
    {
        if (StringUtils.isEmpty(Play.configuration.getProperty("organization.domain")))
        {
            session.put("username", "not authenticated");
            session.put("user", "notauthenticatedId");
        }
        else if (!session.contains("user"))
        {
            login();
        }
    }

    public static void login()
    {
        render();
    }

    public static void authenticate(final String user)
    {
        String domain = Play.configuration.getProperty("organization.domain");

        if (StringUtils.isEmpty(domain))
        {
            session.put("username", "not authenticated");
            session.put("user", "notauthenticatedId");

            redirect(request.controller + ".list");
        }

        if (OpenID.isAuthenticationResponse())
        {

            UserInfo verifiedUser = OpenID.getVerifiedID();

            if (verifiedUser == null)
            {
                flash.error("Oops. Authentication has failed");
                login();
            }
            else
            {
                String userEmail = verifiedUser.extensions.get("email");
                if (!userEmail.endsWith(domain))
                {
                    flash.error("You must have an " + domain + " account to login");
                    login();
                }
                else
                {

                    session.put("username", userEmail.replaceAll("@" + domain, ""));
                    String name1 = verifiedUser.extensions.get("firstname");
                    String name2 = verifiedUser.extensions.get("lastname");
                    session.put("user", verifiedUser.id);
                    flash.success("Welcome " + name1 + " " + name2, null);
                    redirect(request.controller + ".list");
                }
            }
        }
        else
        {
            if (!OpenID.id(user).verify())
            { // will redirect the user

                OpenID.id("https://www.google.com/accounts/o8/id ")
                    .required("firstname", "http://axschema.org/namePerson/first")
                    .required("lastname", "http://axschema.org/namePerson/last")
                    .required("email", "http://schema.openid.net/contact/email").verify();
                UserInfo verifiedUser = OpenID.getVerifiedID();
                login();
            }
        }
    }
}
