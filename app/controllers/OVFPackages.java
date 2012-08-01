package controllers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import models.OVFPackage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;

import play.Play;
import play.i18n.Messages;
import play.jobs.JobsPlugin;
import play.libs.Codec;
import play.libs.OpenID;
import play.libs.OpenID.UserInfo;
import play.libs.WS;
import play.mvc.Before;
import play.mvc.Http.StatusCode;
import play.mvc.Router;
import controllers.DiskID.DiskId;

public class OVFPackages extends CRUD
{

    final static Boolean fullpath = Boolean.TRUE;

    public static void ovfindex()
    {
        request.format = "xml";

        Boolean fullpath = OVFPackages.fullpath;
        List<OVFPackage> ovfpackages = OVFPackage.findAll();
        render(ovfpackages, fullpath);
    }

    public static void createOvf(final OVFPackage object, final File diskFile)
    {
        object.userMail = session.get("username");
        object.unicNameOrAppendTimestamp();
        object.templateVersion = 1;

        if (isUrlSelected(object))
        {
            return;
        }

        if(diskFile == null)
        {
        	response.status = 500;
            renderText("No template disk provided");
            return;
        }
        
        try
        {
	        Future<DiskId> invoc = DiskID.useDiskId(diskFile);
	
	        DiskId diskId = await(invoc);
	        
	        applyDiskId(object, diskId);
        }
        catch (Exception e) 
        {
            play.Logger.error(e, "Can't use DiskID to determine the format");
		}


        object.diskFilePath = FilenameUtils.concat(getRepositoryLocation(), //
            object.name + '.' + FilenameUtils.getExtension(diskFile.getName()));

        checkIsValid(object);
        // TODO check there is space left on the device
        if (new File(object.diskFilePath).exists())
        {
            response.status = 500;
            renderText("path already exists" + object.diskFilePath);
            return;
        }

        Future<String> copy = movingTheDiskIsAnExpensiveOperation(object, diskFile);

        final String moveError = await(copy);
        if (!StringUtils.isEmpty(moveError))
        {
            response.status = 500;
            renderText("Can't save the file in the repository filesystem : " + moveError);
            return;
        }

        object._save();

        redirectToCreated(object);
    }

    protected static void createFromUrl(final OVFPackage object)
    {
        try
        {
            object.diskFileSize =
                Long.valueOf(WS.url(object.diskFilePath).head().getHeader("Content-Length"));

            Future<DiskId> invoc = DiskID.useDiskId(object.diskFilePath, object.diskFileSize);

            DiskId diskId = await(invoc);

            applyDiskId(object, diskId);
        }
        catch (IOException e1)
        {
            response.status = 404;
            renderText("URL not found " + object.diskFilePath);
            // response.status = StatusCode.BAD_REQUEST; //NOT_FOUND
            // renderText("Invalid template disk file URL : " + e1.getMessage());
        }

        checkIsValid(object);

        object._save();

        redirectToCreated(object);
    }

    private static void redirectToCreated(final OVFPackage object)
    {
        final String templateUrl = Router.reverse("OVFPackages.show", new HashMap<String, Object>()
        {
            {
                put("id", object.id.toString());
            }
        }).url;

        play.Logger.info("Template ready at %s", templateUrl);

        response.status = StatusCode.CREATED;
        response.setHeader("Location", templateUrl);
        renderText(templateUrl);
    }

    private static void checkIsValid(final OVFPackage object)
    {
        validation.valid(object);
        if (validation.hasErrors())
        {
            play.Logger.error("Can't create template %s\t %s\t %s\n%s", object.id, object.name,
                object.diskFilePath, validation.errorsMap().toString());
            response.status = 500;
            renderText("Can't validate Template attributes" + validation.errorsMap().toString());
            return;

        }

    }

    private static String urlSelected(final String url)
    {
        if (url.startsWith("http://"))
        {
            return url;
        }
        else if (url.contains("/")) // fucking modern browser copy/paste
        {
            return "http://" + url;
        }
        else
        {
            return null;
        }
    }

    private static boolean isUrlSelected(final OVFPackage object)
    {
        String url = urlSelected(object.diskFilePath);
        if (url != null)
        {
            object.diskFilePath = url;
            createFromUrl(object);
            return true;
        }
        return false;
    }

    private static Future<String> movingTheDiskIsAnExpensiveOperation(final OVFPackage object,
        final File uploaded)
    {

        return JobsPlugin.executor.submit(new Callable<String>()
        {
            @Override
            public String call() throws Exception
            {
                play.Logger.info("Moving to %s", object.diskFilePath);
                try
                {
                    FileUtils.copyFile(uploaded, new File(object.diskFilePath));
                    return null;
                }
                catch (IOException e)
                {
                    play.Logger.error(e, "Template %s FAIL ", object.name);

                    try
                    {
                        new File(object.diskFilePath).delete();
                    }
                    catch (Exception ed)
                    {
                        ed.printStackTrace();
                    }

                    return e.getMessage();
                }
            }
        });
    }

    private static void applyDiskId(final OVFPackage object, final DiskId diskId)
    {
        object.diskFileFormat = diskId.format;
        object.hdInBytes = diskId.hdBytes;
        object.computeSimpleUnits(diskId.hdBytes);

        if(object.hd == 0) // diskId with raw return 2.0K then use the raw size
        {
        	object.hdInBytes = object.diskFileSize;
        	object.computeSimpleUnits(object.diskFileSize);
        }
        
        play.Logger.info("DiskID %s\t %d\tMb", object.diskFileFormat.name(), object.hd);
    }
    

    /** ********** DELETE ********** */

    public static void delete(final String id)
    {
        OVFPackage object = OVFPackage.findById(Long.valueOf(id));
        notFoundIfNull(object);

        if (!object.isDiskUrl())
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

    public static void get(final Long id)
    {
        OVFPackage ovfpackage = OVFPackage.findById(id);
        notFoundIfNull(ovfpackage);

        renderTemplate("OVFPackages/get.xml", ovfpackage);
    }

    public static void getByName(final String name)
    {
        OVFPackage ovfpackage = (OVFPackage) OVFPackage.find("byNameUrl", name).first();
        notFoundIfNull(ovfpackage);

        renderTemplate("OVFPackages/get.xml", ovfpackage);
    }

    public static void getRepository(final String diskFilePath)
    {
        if (diskFilePath.startsWith("http://"))
        {
            redirect(diskFilePath);
        }

        File diskFile = new File(FilenameUtils.concat(getRepositoryLocation(), diskFilePath));

        play.Logger.info("file : %s", diskFilePath);

        renderBinary(diskFile, diskFile.getName());
    }

    private static String getRepositoryLocation()
    {
        String path = Play.configuration.getProperty("ovfcatalog.repositoryPath");

        if (!new File(path).exists())
        {
            throw new RuntimeException("repository folder does not exist " + path);
        }

        return path.endsWith("/") ? path : path.concat("/");
    }

    /**
     * OpenID session authentication
     */

    @Before(unless = {"login", "authenticate", //
        "ovfindex", "getByName", "getRepository"})
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
                    flash.error("You must have an " + domain + " account to log in");
                    login();
                }
                else
                {

                    session.put("username", userEmail);
                    session.put("user", verifiedUser.id);
                    session.put("gravatar", String.format("http://www.gravatar.com/avatar/%s.jpeg", Codec.hexMD5(userEmail)));

                    flash.success(
                        "Welcome %s %s",//
                        verifiedUser.extensions.get("firstname"),
                        verifiedUser.extensions.get("lastname"));
                    
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

                play.Logger.info("Verified user %s", verifiedUser.id);

                login();
            }
        }
    }
}
