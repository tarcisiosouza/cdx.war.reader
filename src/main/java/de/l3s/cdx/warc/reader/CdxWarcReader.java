package de.l3s.cdx.warc.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.warc.WARCReaderFactory;

public final class CdxWarcReader 
{
	
	public static class WarcMapper extends
	Mapper<Object, Text, NullWritable, Text> {

private final static NullWritable outKey = NullWritable.get();
private int rescode;
private String WarcFilePath;
private long offset;
private long compressedsize;
private String CdxFileName;
private String type;
private String WarcFileName;
public String url;
private String[] allMatches = new String[1];
private String str;
private URL Url;

private static HashMap<String, String> domainsCategories = new HashMap<String, String>();
private static HashSet<String> domains = new HashSet<String>();

@Override
protected void setup(Context context) throws IOException,
		InterruptedException {

	Path location = new Path("/user/souza/uk_news_complete.txt");
    FileSystem fileSystem = location.getFileSystem(context.getConfiguration());
    
	RemoteIterator<LocatedFileStatus> fileStatusListIterator = fileSystem.listFiles(
//		            new Path("/Users/tarcisio/Documents/Promotion/german_news.txt"), true);
			new Path("/user/souza/uk_news_complete.txt"), true);
//			new Path("/tarcisio/input/german_news.txt"), true);
    
	while(fileStatusListIterator.hasNext())
	{
    	
		String line;
        LocatedFileStatus fileStatus = fileStatusListIterator.next();
        BufferedReader br=new BufferedReader(new InputStreamReader(fileSystem.open(fileStatus.getPath())));		    
       
        while ((line = br.readLine()) != null) 
        {
        	
        	domains.add(line);
        	
        }
	}
	WarcFilePath = "";
	
	}

public String getDomain (String url) throws MalformedURLException
{
	Matcher m = Pattern.compile("(http).*").matcher(url);
	while (m.find()) 
    {
		
		allMatches[0] = m.group(); 
    	str = allMatches[0];
    	Url = new URL(str);
    }
    
	String Domain = Url.getHost();
	if (Domain.contains("www")) {
		int index = Domain.indexOf(".");
		Domain = Domain.substring(index + 1, Domain.length());
	}
	
	return Domain;
	
}


public void map(Object key, Text value, Context context)
		throws IOException, InterruptedException {

	FileSplit fileSplit = (FileSplit) context.getInputSplit();
	CdxFileName = fileSplit.getPath().getName();
	URL Url;
	
	if (CdxFileName.contains("_SUCESS") || CdxFileName.contains("_index")
			|| CdxFileName.contains("_masterindex"))
		return;

	
	StringTokenizer token = new StringTokenizer(value.toString());
	try 
	{
		token.nextToken();
		token.nextToken();
	} catch (Exception e) {
		return;

	}

	try 
	{
		url = token.nextToken();
		String domain = getDomain(url);
	    
	    	if (!domains.contains(domain))
	    	{ 
	    		return;
	    	}
		type = token.nextToken();
		if (!type.contains("html"))
			return;
		
	} catch (Exception e) {
		return;
	}

	try 
	{
		rescode = Integer.parseInt(token.nextToken());
		if (rescode!=200)
			return;
		int count = 0;
		while (count <= 3)
		{
			token.nextToken();
			count++;
		}
	
		offset = Long.valueOf(token.nextToken()).longValue();
		WarcFileName = token.nextToken();
		
	} catch (Exception e) {
		return;
	}

	try {
		Url = new URL(url.toString());
				
	} catch (Exception e) {
		return;	
	}	
	
	type = WarcFileName.substring(0, 2);
	
	WarcFilePath = "hdfs://nameservice1/data/ia/w/de/" + type + "/" + WarcFileName;
	context.write(outKey, new Text (value.toString()));
/*	
    try {	
    	
    	
		ArchiveRecordIterator (WarcFilePath,offset , context,Url);
	} catch (Exception e) {
		
		String text = e.getMessage();
		context.write(outKey, new Text (text));
		
	}
	*/
}

public String getUrl() {
	return url;
}

public void setUrl(String url) {
	this.url = url;
}

public class ArcWarcFilenameFilter {
	
	public final static String ARC_SUFFIX = ".arc";
	public final static String ARC_GZ_SUFFIX = ".arc.gz";
	public final static String WARC_SUFFIX = ".warc";
	public final static String WARC_GZ_SUFFIX = ".warc.gz";
	public final static String OPEN_SUFFIX = ".open";
	
	
}
public static void ArchiveRecordIterator (String p,long offset, Context context, URL url) throws IOException, InterruptedException {
	  
	 
	  Configuration conf=context.getConfiguration();
	  FileSystem fs=FileSystem.get(conf);
	  Path path=new Path(p);
	  FSDataInputStream is=fs.open(path);
	  
	  is.seek(89653723);
	  
	  ArchiveReader reader = null;
	  
	  
	  if (isArc(path.getName())) {
	    reader=ARCReaderFactory.get(path.getName(),is,false);
	    
	  }
	 else   if (isWarc(path.getName())) {
	    reader=WARCReaderFactory.get(path.getName(),is,false); 
	  
	 }
	  
	  
	  for(ArchiveRecord record : reader)
	    {
				//if (u.contentEquals(url.toString()))
			//	if (record.getHeader().getOffset()==offset)
				context.write (outKey,new Text (record.getHeader().getUrl() + " "+ url + " " + record.getHeader().getOffset()+ " "+ offset));
			    
	    	
	    }

}
	
	private static boolean isArc(final String name) {

		return (name.endsWith(ArcWarcFilenameFilter.ARC_SUFFIX)
				|| name.endsWith(ArcWarcFilenameFilter.ARC_GZ_SUFFIX));
	}

	private static boolean isWarc(final String name) {

		return (name.endsWith(ArcWarcFilenameFilter.WARC_SUFFIX)
			|| name.endsWith(ArcWarcFilenameFilter.WARC_GZ_SUFFIX));	
	}

	
}
	public static void main(String[] args) throws IOException,
	InterruptedException, ClassNotFoundException {

Path inputPath = new Path(args[0]);
Path outputDir = new Path(args[1]);

// Create configuration
Configuration conf = new Configuration(true);
conf.setInt("yarn.nodemanager.resource.memory-mb", 58000);
conf.setInt("yarn.scheduler.minimum-allocation-mb", 3000);
conf.setInt("yarn.scheduler.maximum-allocation-mb", 58000);
conf.setInt("mapreduce.map.memory.mb", 3000);
conf.setInt("mapreduce.reduce.memory.mb", 6000);
conf.setInt("yarn.scheduler.minimum-allocation-mb", 3000);
conf.setInt("yarn.app.mapreduce.am.resource.mb", 6000);
conf.set("mapreduce.map.java.opts", "-Xmx2400m");
conf.set("mapreduce.reduce.java.opts", "-Xmx4800m");
conf.set("mapred.map.child.java.opts", "-Xmx2400m");
// -Xmx512m
conf.set("yarn.app.mapreduce.am.command-opts", "-Xmx4800m");
// conf.setInt("mapreduce.task.io.sort.mb", 15);
conf.setInt("mapreduce.task.io.sort.mb", 1000);

Job job = Job.getInstance(conf);
job.setJarByClass(WarcMapper.class);

// Setup MapReduce
job.setMapperClass(WarcMapper.class);
job.setReducerClass(Reducer.class);
job.setNumReduceTasks(30);

// Specify key / value
job.setOutputKeyClass(NullWritable.class);
job.setOutputValueClass(Text.class);

// Input
FileInputFormat.addInputPath(job, inputPath);
job.setInputFormatClass(TextInputFormat.class);

// Output
FileOutputFormat.setOutputPath(job, outputDir);
job.setOutputFormatClass(TextOutputFormat.class);

// Delete output if exists
FileSystem hdfs = FileSystem.get(conf);
if (hdfs.exists(outputDir))
	hdfs.delete(outputDir, true);

FileOutputFormat.setCompressOutput(job, true);
FileOutputFormat.setOutputCompressorClass(job, GzipCodec.class);
// Execute job
int code = job.waitForCompletion(true) ? 0 : 1;
System.exit(code);

}
}
