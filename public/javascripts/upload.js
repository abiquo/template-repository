function formatChange(format)
{
	// alert(format);
		
	if( format.match(/FLAT$/) )
	{
//		var diskSize = document.getElementById('object_diskFileSize').value;
//		document.getElementById('object_hd').value = diskSize;
//		
//		document.getElementById('object_hdSizeUnit').value=0;
//		document.getElementById('object_hdSizeUnit').option[0].selected=true;
//		document.getElementById('object_hdSizeUnit').option[1].selected=false;		
//		document.getElementById('object_hdSizeUnit').option[2].selected=false;		
//		document.getElementById('object_hdSizeUnit').option[3].selected=false;		
//		document.getElementById('object_hdSizeUnit').option[4].selected=false;		
		
	}
}

function startCreateOVF(createUrl) {

	document.getElementById("progress_bar").style.display= 'block';
	document.getElementById('crudBlank').style.display = 'none';            
    
    
	var diskFile = document.getElementById('diskFile').files[0];

	//if (!diskFile) 
	//{
	//	document.getElementById("uploadFlashFail").style.display= 'block';
	//	document.getElementById("uploadFlashFail").innerHTML= "you browser is not supported, use Chrome.";		
	//}

	var xhr = new XMLHttpRequest();
	xhr.upload.addEventListener("progress", uploadProgress, false);
	xhr.addEventListener("load", uploadComplete, false);
	xhr.addEventListener("error", uploadFailed, false);
	xhr.addEventListener("abort", uploadCanceled, false);

	xhr.open("POST", createUrl);

	if (window.FormData) {
		//XXX not compatible with old Chrome versions
		//var fd = new FormData(document.getElementById("objectFormData"));//new FormData();
					
		var fd = new FormData();//document.getElementById("objectForm").getFormData();
		fd.append("object.hdInBytes", document.getElementById('object_hdInBytes').value);
		fd.append("object.hdSizeUnit", document.getElementById('object_hdSizeUnit').value);
		fd.append("object.diskFilePath", document.getElementById('object_diskFilePath').value);
		fd.append("object.cpu", document.getElementById('object_cpu').value);
		fd.append("object.ram", document.getElementById('object_ram').value);
		fd.append("object.hd", document.getElementById('object_hd').value);
		fd.append("object.ramSizeUnit", document.getElementById('object_ramSizeUnit').value);
		fd.append("object.diskFileSize", document.getElementById('object_diskFileSize').value);
		fd.append("object.diskFileFormat", document.getElementById('object_diskFileFormat').value);
		fd.append("object.description", document.getElementById('object_description').value);
		fd.append("object.name", document.getElementById('object_name').value);
		fd.append("object.categoryName", document.getElementById('object_categoryName').value);
		fd.append("object.iconPath",document.getElementById('object_iconPath').value);
		

		if(diskFile)
		{
			fd.append("diskFile", diskFile);			
		}


		xhr.send(fd);
	}
	else
	{
		document.getElementById("uploadFlashFail").style.display= 'block';
		document.getElementById("uploadFlashFail").innerHTML= "you browser is not supported, use Chrome or Firefox 4.";
	}
}

function uploadFailed(evt) {
	alert("There was an error attempting to upload the file.");
}

function uploadCanceled(evt) {
	alert("The upload has been canceled by the user or the browser dropped the connection.");
}


function uploadProgress(evt)
{
	if (evt.lengthComputable)
	{		
		var progress = (evt.loaded / evt.total) * 100;
		
		if(progress >= 98)
		{
			document.getElementById("progess100").style.display = 'block';        
			document.getElementById("progess100").innerHTML = "Guessing the virtual disk format ...";	
			

			//document.getElementById("ui-progress").style.display= "none";
			document.getElementById("ui-progress").style.width = "100%";
			document.getElementById("ui-progress").value = "100%";

			//document.getElementById("progress_bar").style.display= "none";

		}
		else
		{			
			progress = Math.ceil(progress) + '%';
			
			//document.getElementById("ui-label").value = progress;
			//document.getElementById("progress_value").value = progress;
			
			document.getElementById("ui-progress").style.width = progress;
			document.getElementById("ui-progress").value = progress;
		}
	}
	else
	{
		document.getElementById("progess100").style.display = 'block';        
		document.getElementById("progess100").innerHTML = "can not compute progress";		
	}
}


function uploadComplete(evt)
{

	document.getElementById("progress_bar").style.display= "none";
	document.getElementById("progess100").style.display = 'none';        

	
	
  /* This event is raised when the server send back a response         
   */
  if(evt.target.status == 200)
	{
	  document.getElementById("uploadFlashSuccess").style.display= 'block';
	}
  else
	{
	  document.getElementById("uploadFlashFail").style.display= 'block';
	  document.getElementById("uploadFlashFail").innerHTML=evt.target.responseText;
	  // alert("fail : "+ evt.target.responseText);	
	}
}

////http://jquery-html5-upload.googlecode.com/svn/trunk/jquery.html5_upload.js
//// https://developer.mozilla.org/en/FileGuide/FileUpDown

