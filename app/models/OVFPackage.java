package models;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.xml.ws.WebServiceException;

import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;
import play.libs.Codec;
import play.mvc.Router;

@Entity
public class OVFPackage extends Model
{
    @Required
    @Unique
    @MaxSize(256)
    public String name;

    @Required
    @Enumerated(EnumType.STRING)
    public DiskFormatType diskFileFormat;

    @MaxSize(1024)
    public String description;

    @Required
    @Unique
    @MaxSize(512)
    public String diskFilePath;

    @Required
    public Long diskFileSize;

    public Integer cpu;

    public Long ram;

    public Long hd;

    public Long hdInBytes;

    @Enumerated(EnumType.STRING)
    public MemorySizeUnitType ramSizeUnit;

    @Enumerated(EnumType.STRING)
    public MemorySizeUnitType hdSizeUnit;

    @MaxSize(256)
    public String iconPath;

    @MaxSize(256)
    public String categoryName;

    @MaxSize(256)
    public String userMail;

    @Enumerated(EnumType.STRING)
    public EthernetDriver ethernetDriver;

    public Integer templateVersion;

    public void setTemplateVersion(final Integer templateVersion)
    {
        this.templateVersion = templateVersion;
    }

    public Integer getTemplateVersion()
    {
        return templateVersion != null ? templateVersion : 0;    
    }

    // DEFAULTS
    private final static Integer CPU_DEFAULT = 1;

    private final static Long RAM_DEFAULT = 512l;

    private final static Long HD_DEFAULT = 2l;

    public Integer getCpu()
    {
        return cpu != null ? cpu : CPU_DEFAULT;
    }

    public Long getHd()
    {
        return hd != null ? hd : HD_DEFAULT;
    }

    public String getCategoryName()
    {
        return categoryName != null ? categoryName : "Others";
    }

    public Long getHdInBytes()
    {
        return getHdSizeUnit() == MemorySizeUnitType.BYTE ? getHd() : hdInBytes(getHd().doubleValue(), getHdSizeUnit());
    }

    public static Long hdInBytes(final Double hd, final MemorySizeUnitType units)
    {
        Double hdB = hd;

        switch (units)
        {
            case TB:
                hdB = hdB * 1024;
            case GB:
                hdB = hdB * 1024;
            case MB:
                hdB = hdB * 1024;
            case KB:
                hdB = hdB * 1024;
            default:
                break;
        }

        return hdB.longValue();

    }

    public Long getRam()
    {
        return ram != null ? ram : RAM_DEFAULT;
    }

    public MemorySizeUnitType getRamSizeUnit()
    {
        return ramSizeUnit != null ? ramSizeUnit : MemorySizeUnitType.MB;
    }

    public MemorySizeUnitType getHdSizeUnit()
    {
        return hdSizeUnit != null ? hdSizeUnit : MemorySizeUnitType.GB;
    }

    public String getIconPath()
    {

        return iconPath != null ? iconPath : Router.getFullUrl("OVFPackages.list")
            + "public/icons/q.png";
    }

    public DiskFormatType getDiskFileFormat()
    {
        return diskFileFormat != null ? diskFileFormat : DiskFormatType.VMDK_STREAM_OPTIMIZED;
    }

    public EthernetDriver getEthernetDriver()
    {
        return ethernetDriver != null ? ethernetDriver : EthernetDriver.E1000;
    }

    public void setEthernetDriver(final EthernetDriver ethernetDriver)
    {
        this.ethernetDriver = ethernetDriver;
    }
    
    final static DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
    .appendMonthOfYearShortText()
    .appendDayOfMonth(2).appendLiteral('_')//
    .appendHourOfDay(2).appendLiteral("-").appendMinuteOfHour(2).toFormatter();

    
    public void unicNameOrAppendTimestamp() 
    {
        // remove extension from default name (file name)
        try
        {
            name =  URLEncoder.encode(FilenameUtils.getBaseName(name), "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new WebServiceException(e);
        }

        // check name is unique or append time-stamp
        if (find("name", name).first() != null)
        {
            name = name +"_"+new DateTime().toString(TIME_FORMATTER);
        }
    }
    

    public OVFPackage()
    {

    }

    public OVFPackage(final String name, final String description,
        final DiskFormatType diskFileFormat, final String diskFilePath, final Long diskFileSize,
        final Integer cpu, final Long ram, final Long hd, final MemorySizeUnitType ramSizeUnit,
        final MemorySizeUnitType hdSizeUnit, final String iconPath, final String categoryName,
        final String user)
    {
        super();

        this.userMail = user;
        this.name = name;
        this.diskFileFormat = diskFileFormat;
        this.description = description;
        this.diskFilePath = diskFilePath;
        this.diskFileSize = diskFileSize;
        this.cpu = cpu;
        this.ram = ram;
        this.hd = hd;
        this.ramSizeUnit = ramSizeUnit;
        this.hdSizeUnit = hdSizeUnit;
        this.iconPath = iconPath;
        this.categoryName = categoryName;
    }

    @Override
    public String toString()
    {
        return String.format("name:%s, format:%s", name, getDiskFileFormat().name());
    }

    public enum MemorySizeUnitType
    {
        BYTE, KB, MB, GB, TB;

        @Override
        public String toString()
        {
            return this.name();
        }
    }

    public enum EthernetDriver
    {
        /* 0 */ E1000,
        /* 1 */ PCNet32, 
        /* 2 */ VMXNET3
    }

    public enum DiskFormatType
    {
        /* 0 */UNKNOWN("http://unknown"),
        /* 1 */RAW("http://raw"),
        /* 2 */INCOMPATIBLE("http://incompatible"),
        /* 3 */VMDK_STREAM_OPTIMIZED("http://www.vmware.com/technical-resources/interfaces/vmdk_access.html#streamOptimized"),
        /* 4 */VMDK_FLAT("http://www.vmware.com/technical-resources/interfaces/vmdk_access.html#monolithic_flat"),
        /* 5 */VMDK_SPARSE("http://www.vmware.com/technical-resources/interfaces/vmdk_access.html#monolithic_sparse"),
        /* 6 */VHD_FLAT("http://technet.microsoft.com/en-us/virtualserver/bb676673.aspx#monolithic_flat"),
        /* 7 */VHD_SPARSE("http://technet.microsoft.com/en-us/virtualserver/bb676673.aspx#monolithic_sparse"),
        /* 8 */VDI_FLAT("http://forums.virtualbox.org/viewtopic.php?t=8046#monolithic_flat"),
        /* 9 */VDI_SPARSE("http://forums.virtualbox.org/viewtopic.php?t=8046#monolithic_sparse"),
        /* 10 */QCOW2_FLAT("http://people.gnome.org/~markmc/qcow-image-format.html#monolithic_flat"),
        /* 11 */QCOW2_SPARSE("http://people.gnome.org/~markmc/qcow-image-format.html#monolithic_sparse");

        private final String url;

        private DiskFormatType(final String url)
        {
            this.url = url;
        }

        public String url()
        {
            return url;
        }

        @Override
        public String toString()
        {
            return this.name();
        }
    }

    public boolean isDiskUrl()
    {
        return diskFilePath.startsWith("http://");
    }

    public void computeSimpleUnits(final long hdInBytes)
    {
        this.hd = hdInBytes / 1048576;
        this.hdSizeUnit = MemorySizeUnitType.MB;
    }
    
    
    public String gravatarCreator()
    {
        return userMail == null || userMail.equalsIgnoreCase("not authenticated")?"http://www.gravatar.com/avatar/default.jpeg"://
            String.format("http://www.gravatar.com/avatar/%s.jpeg", Codec.hexMD5(userMail));
    }


}
