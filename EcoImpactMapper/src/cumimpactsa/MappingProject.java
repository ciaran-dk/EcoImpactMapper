/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cumimpactsa;

import java.awt.Color;
import java.util.List;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.nio.file.StandardCopyOption.*;
import javax.swing.JOptionPane;

/**
 * @summary This class represents a human impact mapping project.
 * @author ast
 */
public class MappingProject 
{
    public MappingGrid grid=null;
   
    public ArrayList<SpatialDataLayer> stressors = new ArrayList<SpatialDataLayer>();
    public ArrayList<SpatialDataLayer> ecocomps = new ArrayList<SpatialDataLayer>();
    public ArrayList<PreProcessor> processors = new ArrayList<PreProcessor>();
    public SensitivityScoreSet sensitivityScores = new SensitivityScoreSet();
    public SpatialDataLayer regions = null;
    
    private String projectPath;
    
    private SpatialDataLayer lastDataAdded; 
    
    public ArrayList<DrawableData> results=new ArrayList<DrawableData>();    //processing results, e.g. impact indices
    
    public String projectFolder="";
    
    public boolean processing = false;
    private int processingProgressPercent=-1;
    
    public void reset()
    {
        grid=null;
        stressors = new ArrayList<SpatialDataLayer>();
        ecocomps = new ArrayList<SpatialDataLayer>();
        lastDataAdded=null;
        sensitivityScores=new SensitivityScoreSet();
        results=new ArrayList<DrawableData>();
        regions = null;
        processing=false;
        processingProgressPercent=-1;
        
    }
    
    public synchronized void setProcessingProgressPercent(int percent)
    {
        processingProgressPercent=percent;
    }
    
    public int getProcessingProgressPercent()
    {
        return processingProgressPercent;
    }
    
    public void initializeProcessors()
    {
        processors = new ArrayList<PreProcessor>();
        processors.add(new IdentityProcessor());
        processors.add(new LogTransformer());
        processors.add(new Rescaler()); 
        
    }
    
    public String[] getProcessorNames()
    {
        String[] names = new String[processors.size()];
        for(int i=0; i<processors.size();i++) {names[i]=processors.get(i).getName();}
        return names;
    }
    
    public void addData(String name, DataGrid grid, int type, DataSourceInfo source)
    {
        //remove commas from name 
        name=name.replace(',',';');
        SpatialDataLayer layer = new SpatialDataLayer(name, grid, type, source);
        if(type==GlobalResources.DATATYPE_ECOCOMP)
            {ecocomps.add(layer);}
        else if(type==GlobalResources.DATATYPE_STRESSOR)
            {stressors.add(layer);}
        else if(type==GlobalResources.DATATYPE_SPATIAL)
            {results.add(layer);}
        else if(type==GlobalResources.DATATYPE_REGIONS)
            {
                regions=layer;
                SpatialDataLayer oldRegions=getDataLayerByName(regions.getName());
                if(oldRegions!=null) {results.remove(oldRegions);}
                results.add(regions);
            }
        
        lastDataAdded=layer;
    }

 
    
    public SpatialDataLayer getRegions()
    {
        return regions;
    }
    
    public String[] getStressorNames()
    {
        String[] result = new String[stressors.size()];
        for(int i=0; i<stressors.size();i++)
        {
            result[i]=stressors.get(i).getName();
        }
        return result;
    }
    
    public String[] getResultNames()
    {
        String[] result = new String[results.size()];
        for(int i=0; i<results.size();i++)
        {
            result[i]=results.get(i).getName();
        }
        return result;
    }
    
    public SpatialDataLayer getLastDataAdded()
    {
        return lastDataAdded;
    }
    
    public SpatialDataLayer getStressorByName(String name)
    {
        
        if(name==null) return null;
        
        String[] stressorNames=getStressorNames();
        for(int i=0; i<stressorNames.length;i++)
        {
            if(stressorNames[i].trim().equalsIgnoreCase(name.trim()))
                {return stressors.get(i);}
        }
        return null;
    }
    
    public void removeStressorByName(String name)
    {
        String[] stressorNames=getStressorNames();
        for(int i=0; i<stressorNames.length;i++)
        {
            if(stressorNames[i].equals(name))
            {
                stressors.remove(i);
            }   
        }
    }
    
    public String[] getEcocompNames()
    {
        String[] result = new String[ecocomps.size()];
        for(int i=0; i<ecocomps.size();i++)
        {
            result[i]=ecocomps.get(i).getName();
        }
        return result;
    }
    

    
    public SpatialDataLayer getEcocompByName(String name)
    {
        if(name==null) return null;
        
        String[] names=getEcocompNames();
        for(int i=0; i<names.length;i++)
        {
            if(names[i].trim().equalsIgnoreCase(name.trim()))
                {return ecocomps.get(i);}
        }
        return null;
    }
    
    public SpatialDataLayer getDataLayerByName(String name)
    {
        SpatialDataLayer layer=getStressorByName(name);
        if(layer==null) {layer=getEcocompByName(name);}
        if(layer==null) 
        {
            DrawableData result=getResultByName(name);
            if(result!=null && result.getDrawingDataType()==GlobalResources.DATATYPE_SPATIAL)
            {
                layer = (SpatialDataLayer) result;
            }
        }
        return layer;
    }
    
    public void removeEcocompByName(String name)
    {
        String[] names=getEcocompNames();
        for(int i=0; i<names.length;i++)
        {
            if(names[i].equals(name))
            {
                ecocomps.remove(i);
            }   
        }
    }
    
    public PreProcessor getNewProcessorByName(String name)
    {
        for(int i=0; i<processors.size();i++)
        {
            if(processors.get(i).getName().equals(name))
            {
                return processors.get(i).clone();
            }
        }    
            
        return null;
    }
    
    public void save(String filename)
    {
        projectFolder = Paths.get(filename).getParent().toString();
        
        CsvTableGeneral table=new CsvTableGeneral();
        table.addColumn("ResType");
        table.addColumn("ResName");
        table.addColumn("File");
        table.addColumn("XField");
        table.addColumn("YField");
        table.addColumn("ValueField");
        //write stressor sources
        for(int i=0; i<stressors.size();i++)
        {
           ArrayList<String> row = new ArrayList<String>();
           row.add("stressor");
           row.add(stressors.get(i).getName());
           row.add(getRelativePath(stressors.get(i).getSource().sourceFile));
           row.add(stressors.get(i).getSource().xField);
           row.add(stressors.get(i).getSource().yField);
           row.add(stressors.get(i).getSource().valueField);
           table.addRow(row);
        }
        
        //write ecocomp sources
         for(int i=0; i<ecocomps.size();i++)
        {
           ArrayList<String> row = new ArrayList<String>();
           row.add("ecocomp");
           row.add(ecocomps.get(i).getName());
           row.add(getRelativePath(ecocomps.get(i).getSource().sourceFile));
           row.add(ecocomps.get(i).getSource().xField);
           row.add(ecocomps.get(i).getSource().yField);
           row.add(ecocomps.get(i).getSource().valueField);
           table.addRow(row);
        }
         
         //write results
         for(int i=0; i<results.size();i++)
        {
           if(results.get(i).getDrawingDataType()!=GlobalResources.DATATYPE_REGIONS && !results.get(i).getName().equals("Regions"))
           { 
                ArrayList<String> row = new ArrayList<String>();
                row.add("result");
                row.add(results.get(i).getName());
                row.add(getRelativePath(results.get(i).getSource().sourceFile));
                row.add(results.get(i).getSource().xField);
                row.add(results.get(i).getSource().yField);
                row.add(results.get(i).getSource().valueField);
                table.addRow(row);
           }
        }
         
        //write sensitivity score source, if set
        if(sensitivityScores!=null && sensitivityScores.getAllScores().size()>0 && sensitivityScores.getSourceFileName()!=null && sensitivityScores.getSourceFileName().length()>0) 
        {
            ArrayList<String> scoreRow = new ArrayList<String>();
            scoreRow.add("sensitivityscorefile");
            scoreRow.add("n/a");
            scoreRow.add(getRelativePath(sensitivityScores.getSourceFileName()));
            scoreRow.add("n/a");scoreRow.add("n/a");scoreRow.add("n/a");
            table.addRow(scoreRow);
        }
        
         //add path to sourcefile for processing chain
        String basepath;
        int pos=filename.lastIndexOf(".");
        if(pos<1) {basepath=filename;}
        else {basepath = filename.substring(0,pos);}
        String procFileName=basepath+"_pchains.csv";
        ArrayList<String> row = new ArrayList<String>();
        row.add("processingchains");
        row.add("n/a");
        row.add(getRelativePath(procFileName));
        row.add("n/a");row.add("n/a");row.add("n/a");
        table.addRow(row);
        //create processing chain file
        saveProcessingChains(procFileName);
        
        //add path to sourcefile for data types
        pos=filename.lastIndexOf(".");
        if(pos<1) {basepath=filename;}
        else {basepath = filename.substring(0,pos);}
        String dataTypeName=basepath+"_datatypes.csv";
        row = new ArrayList<String>();
        row.add("datatypes");
        row.add("n/a");
        row.add(getRelativePath(dataTypeName));
        row.add("n/a");row.add("n/a");row.add("n/a");
        table.addRow(row);
        //create processing chain file
        saveDataTypes(dataTypeName);
        
        //save regions
        if(regions!=null)
        {    
            row = new ArrayList<String>();
            row.add("regions");
            row.add(regions.getName());
            row.add(getRelativePath(regions.getSource().sourceFile));
            row.add(regions.getSource().xField);
            row.add(regions.getSource().yField);
            row.add(regions.getSource().valueField);
            table.addRow(row);
        }
        
        //save colors
        ArrayList<String> maxRow = new ArrayList<String>();
        ArrayList<String> midRow = new ArrayList<String>();
        ArrayList<String> minRow = new ArrayList<String>();
        maxRow.add("color"); midRow.add("color"); minRow.add("color");
        maxRow.add("maxcolor"); midRow.add("midcolor"); minRow.add("mincolor");
        maxRow.add("n/a"); midRow.add("n/a"); minRow.add("n/a");
        maxRow.add("n/a"); midRow.add("n/a"); minRow.add("n/a");
        maxRow.add("n/a"); midRow.add("n/a"); minRow.add("n/a");
        maxRow.add(ImageCreator.maxColor.getRGB()+""); midRow.add(ImageCreator.midColor.getRGB()+""); minRow.add(ImageCreator.minColor.getRGB()+"");
        table.addRow(maxRow);table.addRow(midRow);table.addRow(minRow);
        
        
        
        table.writeToFile(filename);
        
    }
    
    //Turns an absolute path into a path relative to the project folder.
    private String getRelativePath(String absPath)
    {
        Path pathAbs = Paths.get(absPath);
        Path pathBase = Paths.get(projectFolder);
        return pathBase.relativize(pathAbs).toString();
    }
    
    private void saveProcessingChains(String filename)
    {
        CsvTableGeneral table = new CsvTableGeneral();
        table.addColumn("DataLayer");
        table.addColumn("Processor");
        
        //go through stressors
        for(int i=0; i<stressors.size();i++)
        {
            ArrayList<PreProcessor> procChain = stressors.get(i).getProcessingChain();
            if(procChain.size()==0)
            {
                procChain.add(new IdentityProcessor());
            }
            for(int j=0; j<procChain.size();j++)
            {
                ArrayList<String> row = new ArrayList<String>();
                row.add(stressors.get(i).getName());
                row.add(procChain.get(j).getName());
                table.addRow(row);
            }     
        }

        for(int i=0; i<ecocomps.size();i++)
        {
            ArrayList<PreProcessor> procChain = ecocomps.get(i).getProcessingChain();
            if(procChain.size()==0)
            {
                procChain.add(new IdentityProcessor());
            }
            for(int j=0; j<procChain.size();j++)
            {
                ArrayList<String> row = new ArrayList<String>();
                row.add(ecocomps.get(i).getName());
                row.add(procChain.get(j).getName());
                table.addRow(row);
            }     
        }
        
        table.writeToFile(filename);
    }
    
    private void saveDataTypes(String filename)
    {
        CsvTableGeneral table = new CsvTableGeneral();
        table.addColumn("DataLayer");
        table.addColumn("DataType");
        
        //go through stressors
        for(int i=0; i<stressors.size();i++)
        {
            String dataType = stressors.get(i).getSpatialDataType();
            ArrayList<String> row = new ArrayList<String>();
            row.add(stressors.get(i).getName());
            row.add(dataType);
            table.addRow(row);     
        }

        for(int i=0; i<ecocomps.size();i++)
        {
            String dataType = ecocomps.get(i).getSpatialDataType();
            ArrayList<String> row = new ArrayList<String>();
            row.add(ecocomps.get(i).getName());
            row.add(dataType);
            table.addRow(row);     
        }
        
        table.writeToFile(filename);
    }
    
    
    public void createFromTable(CsvTableGeneral table)
    {
        //load all info
        reset();
        processing=true;
        final ArrayList<String> resType = table.getColumn("ResType");
        final ArrayList<String> resName = table.getColumn("ResName");
        final ArrayList<String> file = table.getColumn("File");
        final ArrayList<String> xField = table.getColumn("XField");
        final ArrayList<String> yField = table.getColumn("YField");
        final ArrayList<String> valueField = table.getColumn("ValueField");
        //final StatusWindow statusWindow = new StatusWindow();
        final CsvTableGeneral procTable=new CsvTableGeneral();
        final CsvTableGeneral dataTypeTable=new CsvTableGeneral();
        //statusWindow.setVisible(true);
        
        //do loading in worker thread
        SwingWorker<Boolean, Integer> worker = new SwingWorker<Boolean, Integer>() 
        {
             @Override
             protected Boolean doInBackground() throws Exception 
             {
                
                 //go through line-by-line and load data
                for(int row=0; row<resType.size(); row++)
                { 
                    processingProgressPercent=(int) (100*row/resType.size());
                    //load spatial data
                    
                    System.out.println("Loading: " + row + " Value: " + valueField.get(row));
                    
                    if(resType.get(row).equals("stressor") || resType.get(row).equals("ecocomp") || resType.get(row).equals("result") || resType.get(row).equals("regions"))
                    {
                        //System.out.println("  SPATIAL");
                        int dataType=-1;
                        if(resType.get(row).equals("stressor")) {dataType=GlobalResources.DATATYPE_STRESSOR;}
                        else if(resType.get(row).equals("ecocomp")) {dataType=GlobalResources.DATATYPE_ECOCOMP;}
                        else if(resType.get(row).equals("result")) {dataType=GlobalResources.DATATYPE_SPATIAL;}
                        else if(resType.get(row).equals("regions")) {dataType=GlobalResources.DATATYPE_REGIONS;}
                        
                        //read input file for this row, if not last loaded and still open
                        CsvTableFloat inputData;
                        if(GlobalResources.lastOpenedTableFile.equals(getAbsolutePath(file.get(row))))
                        {
                            inputData = GlobalResources.lastOpenedTable;
                        }
                        else
                        {
                            inputData = new CsvTableFloat(new File(getAbsolutePath(file.get(row))));
                            GlobalResources.lastOpenedTable=inputData;
                            GlobalResources.lastOpenedTableFile=getAbsolutePath(file.get(row));
                        }

                        double[] x = inputData.getColumn(xField.get(row));
                        double[] y = inputData.getColumn(yField.get(row));
                        double[] values = inputData.getColumn(valueField.get(row));
                
                        //make mapping grid if necessary
                        if(grid==null)
                        {
                            grid = new MappingGrid(x,y);
                        }
                    
                        DataGrid datagrid = grid.createDataGrid(x, y, values);
                        DataSourceInfo info = new DataSourceInfo();
                        info.sourceFile=getAbsolutePath(file.get(row));
                        info.valueField=valueField.get(row);
                        info.xField=xField.get(row);
                        info.yField=yField.get(row);
                        addData(resName.get(row), datagrid, dataType, info);
                
                    }
                   else if(resType.get(row).equals("processingchains"))
                   {
                        //load table
                       String procFileName=getAbsolutePath(file.get(row));
                       procTable.readFromFile(new File(procFileName));
                       //processing of this table will occur when all other data are loaded to make sure no data layers are missing
                   }
                   else if(resType.get(row).equals("datatypes"))
                   {
                        //load table
                       String dataTypeFileName=getAbsolutePath(file.get(row));
                       dataTypeTable.readFromFile(new File(dataTypeFileName));
                       //processing of this table will occur when all other data are loaded to make sure no data layers are missing
                   }
                   else if(resType.get(row).equals("color"))
                   {
                       if(resName.get(row).equals("maxcolor")) 
                       {
                           int col=Integer.parseInt(valueField.get(row));
                           ImageCreator.maxColor=new Color(col);
                       }
                       else if(resName.get(row).equals("midcolor")) 
                       {
                           int col=Integer.parseInt(valueField.get(row));
                           ImageCreator.midColor=new Color(col);
                       }
                       else if(resName.get(row).equals("mincolor")) 
                       {
                           int col=Integer.parseInt(valueField.get(row));
                           ImageCreator.minColor=new Color(col);
                       }
                   }
                   else if(resType.get(row).equals("sensitivityscorefile"))
                   {
                       sensitivityScores=new SensitivityScoreSet();
                       sensitivityScores.createFromFile(getAbsolutePath(file.get(row)));
                   }

                    
                }

        
                //project file contained reference to a processing chain table
                if(procTable.getColNames().size()>0)
                {
                    ArrayList<String> layers=procTable.getColumn("DataLayer");
                    ArrayList<String> processors=procTable.getColumn("Processor");
            
                    for(int i=0; i<layers.size();i++)
                    {
                        SpatialDataLayer layer = getDataLayerByName(layers.get(i));
                        if(!layer.isProcessingChainLoaded())
                        {
                            layer.getProcessingChain().clear();  //remove default list
                            layer.setProcessingChainLoaded();
                        }
                        PreProcessor processor = getNewProcessorByName(processors.get(i));
                        layer.getProcessingChain().add(processor);
                    }
                }
                
                //project file contained reference to a data type table
                if(dataTypeTable.getColNames().size()>0)
                {
                    ArrayList<String> layers=dataTypeTable.getColumn("DataLayer");
                    ArrayList<String> types=dataTypeTable.getColumn("DataType");
                    for(int i=0; i<layers.size();i++)
                    {
                        SpatialDataLayer layer = getDataLayerByName(layers.get(i));
                        layer.setSpatialDataType(types.get(i));
                    }
                }
                 
                processing=false;
                return true;
                
            }
             
	};

	worker.execute();

        
    }

    //Creates an absolute path from the project folderand a path relative to it
    private String getAbsolutePath(String relPath)
    {
        Path pathRel= Paths.get(relPath);
        Path pathBase = Paths.get(projectFolder);
        return pathBase.resolve(pathRel).toString();
    }
            
    public void removeResultByName(String resultName) {
        String[] names=getResultNames();
        for(int i=0; i<names.length;i++)
        {
            if(names[i].equals(resultName))
            {
                results.remove(i);
            }   
        }
    }

    public DrawableData getResultByName(String resultName) 
    {
        
        if(resultName==null) return null;
        
        String[] names=getResultNames();
        for(int i=0; i<names.length;i++)
        {
            if(names[i].trim().equalsIgnoreCase(resultName.trim()))
                {return results.get(i);}
        }
        return null;
    }

    
}
