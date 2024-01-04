package pointFunctions;


//import java.util.Random;

/*
 * N-Point Probability functions
 * 1-Point The likelihood that a random point will lie is the selected component e.g. porosity
 * 2-Point The likelihood that two random points separated by a distance L will lie is the selected
 * component e.g. as f(L) autocorrelation
 * 
 * Higher order N-point functions are not currently supported due to lack of meaningful interpretation..
 * 
 * Chord Length - The likelihood of finding a a line chord of a given length lying in the selected component.
 * Lineal Path	- The likelihood of a line of length L will lie completely in the selected component.
 * Pore Size	- The likelihood of finding a pore of a given size in the image, e.g. pore size distribution. 
 */


import java.awt.*;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.filter.*;
import ij.process.ImageProcessor;

import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.*;
import jhd.PointFunctions.*;


public class Pore_Size_Distribution implements PlugInFilter, DialogListener
{

	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff

	String[] sliceChoices = {"Current Slice","All Slices 2D","All Slices 3D"};
	String[] sliceChoices1= {"Current Slice"};
	String[] valChoices = {"Map 0","Map !0"};

	ImagePlus imp,sliceImp;
	ImageStack stack;
	double maxProbeSize;
	class DialogParams
	{
		String sliceChoice;
		String valChoice;
		int nBins,nSamples;
		double probeDim;
		boolean showProgress;
	}

	PointFunctions pf = new PointFunctions();
	NumericField probeSizeNF,numBinsNF,numSamplesNF;
	ChoiceField calcChoiceCF;

	int maxR;

	//*********************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_8G + DOES_16 + DOES_32;
	}

	//*********************************************************************************************

	private DialogParams DoMyDialog()
	{
		DialogParams dp = new DialogParams();
		//Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		//find the largest image dimension
		int imgWidth,imgHeight,imgDepth;
		imgWidth=imp.getWidth();
		imgHeight=imp.getHeight();
		imgDepth=imp.getImageStackSize();
		Calibration cal = imp.getCalibration();
		double pixelWidth = cal.pixelWidth;
		double pixelHeight = cal.pixelHeight;
		double pixelDepth = cal.pixelDepth;

		if(imgDepth==1) sliceChoices = sliceChoices1;

		maxR=pf.getMaxPixelSeparationDistance(imgWidth, imgHeight, imgDepth);
		maxProbeSize =pf.getMaxProbeSize(imgWidth, imgHeight, imgDepth, pixelWidth,pixelHeight,pixelDepth);

		GenericDialog gd = new GenericDialog("Pore Size Distribution");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.addMessage("Experimental!\nFor research use only",myFont);
		gd.addRadioButtonGroup("Value to process", valChoices, 1, 2, valChoices[0]);
		gd.addChoice("Calculate",sliceChoices,sliceChoices[0]);
		calcChoiceCF = gda.getChoiceField(gd, null, "calcChoice");
		gd.addMessage("Max Probe Size ="+maxProbeSize);
		gd.addNumericField("Pore Probe Size", maxProbeSize/4);
		probeSizeNF = gda.getNumericField(gd, null, "probeSize");
		gd.addNumericField("Number of Bins", maxR);
		numBinsNF = gda.getNumericField(gd, null, "numBins");
		gd.addNumericField("Number of Samples", 10000);
		numSamplesNF = gda.getNumericField(gd, null, "numSamples");
		gd.addCheckbox("Show Progress", true);
		gd.addHelp("https://lazzyizzi.github.io/index.html");
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		dp.valChoice = gd.getNextRadioButton();
		dp.sliceChoice = gd.getNextChoice();
		dp.probeDim = gd.getNextNumber();
		dp.nBins = (int) gd.getNextNumber();
		dp.nSamples = (int) gd.getNextNumber();
		dp.showProgress = gd.getNextBoolean();

		return dp;
	}

	//*********************************************************************************************

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		boolean dialogOK=true;
		if(e!=null)
		{
			Object src = e.getSource();
			if(src instanceof TextField)
			{
				TextField tf = (TextField)src;
				String name  = tf.getName();
				double theNumber;
				switch(name)
				{
				case "probeSize":
					theNumber = probeSizeNF.getNumber();
					if(Double.isNaN(theNumber) || theNumber > maxProbeSize || theNumber <0)
					{
						probeSizeNF.getNumericField().setBackground(Color.RED);
						dialogOK=false;
					}
					else
					{
						probeSizeNF.getNumericField().setBackground(Color.WHITE);
						dialogOK=true;
					}
					break;
				case "numBins":
					theNumber = numBinsNF.getNumber();
					if(Double.isNaN(theNumber) || theNumber < 8)
					{
						numBinsNF.getNumericField().setBackground(Color.RED);
						dialogOK=false;
					}
					else
					{
						numBinsNF.getNumericField().setBackground(Color.WHITE);
						dialogOK=true;
					}
					break;

				case "numSamples":
					theNumber = numSamplesNF.getNumber();
					if(Double.isNaN(theNumber) || theNumber <=0)
					{
						numSamplesNF.getNumericField().setBackground(Color.RED);
						dialogOK=false;
					}
					else
					{
						numSamplesNF.getNumericField().setBackground(Color.WHITE);
						dialogOK=true;
					}
					break;
				}

			}
			if(src instanceof Choice)
			{
				Choice choice = (Choice)src;
				String name  = choice.getName();
				switch(name)
				{
				case "calcChoice":
					break;
				}
			}
		}
		return dialogOK;
	}

	//*********************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		Calibration cal = imp.getCalibration();		
		String xUnit = cal.getXUnit();
		String yUnit = cal.getYUnit();
		String zUnit = cal.getZUnit();
		if(!xUnit.equals(yUnit) || !xUnit.equals(zUnit) )
		{
			IJ.error("Image X,Y,Z must have the same units");
			return;
		}
		String unit = cal.getUnit();
		
		DialogParams dp = DoMyDialog();
		Plot plot;
		PointFunctions.Histogram hist1;
		
		int	imgWidth=imp.getWidth();
		int	imgHeight=imp.getHeight();
		int	imgDepth=imp.getNSlices();
		double	pixelWidth=imp.getCalibration().pixelWidth;
		double	pixelHeight=imp.getCalibration().pixelHeight;
		double	pixelDepth=imp.getCalibration().pixelDepth;

		pf.initRandom();

		if(dp!=null)
		{
			String title,caption, capStr ="Pore Size Distribution";
			
			switch((dp.sliceChoice))
			{
			case "Current Slice":
				sliceImp = imp.crop("whole-slice");
				Object[] image = sliceImp.getStack().getImageArray();
				imgWidth=sliceImp.getWidth();
				imgHeight=sliceImp.getHeight();
				imgDepth=sliceImp.getNSlices();
				
				hist1= pf.poreSizeDistribution(image,imgWidth,imgHeight,imgDepth,dp.probeDim,
						pixelWidth,pixelHeight,pixelDepth,
						dp.nBins,dp.nSamples, dp.valChoice, dp.showProgress);
				sliceImp.close();
				
				title = imp.getShortTitle() + "_PoreSize_Slice_" + imp.getCurrentSlice() + "_P"+dp.probeDim + "_N"+dp.nSamples+ "_B"+dp.nBins+"_"+ dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				caption = capStr +  "\nSlice " +imp.getCurrentSlice()+ "\nProbe "+dp.probeDim + "\nN "+dp.nSamples+ "\nBins "+dp.nBins+"\n" + dp.valChoice;
				
				plot = new Plot(title,"Estimated Radius(("+unit+"))","Probability Density");
				plot.setBackgroundColor(myColor);
				plot.setColor("black");
				plot.setFont(myFont);
				plot.addLabel(0.7, 0.1, caption);
				plot.setColor("red");
				plot.setLineWidth(2);
				plot.add("line", hist1.bin, hist1.count);
				plot.show();
				break;

			case "All Slices 2D":
				ResultsTable rt = new ResultsTable();
				double maxCnt=Float.MIN_VALUE;
				double maxBin=Float.MIN_VALUE;
				double minCnt=Float.MAX_VALUE;
				double minBin=Float.MAX_VALUE;

				int slices = imp.getStack().getSize();
				for(int i = 0; i<slices; i++)
				{
					imp.setSlice(i+1);
					sliceImp = imp.crop("whole-slice");
					image = sliceImp.getStack().getImageArray();
					imgWidth=sliceImp.getWidth();
					imgHeight=sliceImp.getHeight();
					imgDepth=sliceImp.getNSlices();
					
					hist1= pf.poreSizeDistribution(image,imgWidth,imgHeight,imgDepth,dp.probeDim,
							pixelWidth,pixelHeight,pixelDepth,
							dp.nBins,dp.nSamples, dp.valChoice, false);

					for (int j =0;j<hist1.bin.length;j++)
					{
						rt.setValue("Bin", j, hist1.bin[j]);
						rt.setValue("Slice " + (i+1), j, hist1.count[j]);
						if(hist1.count[j]> maxCnt) maxCnt=hist1.count[j];
						if(hist1.bin[j]> maxBin) maxBin=hist1.bin[j];
						if(hist1.count[j]< minCnt) minCnt=hist1.count[j];
						if(hist1.bin[j]< minBin) minBin=hist1.bin[j];
					}
					sliceImp.close();
					IJ.showProgress((double)i/(double)slices);
				}
				title = imp.getShortTitle() + "_PoreSize_" + dp.sliceChoice+ "_P"+dp.probeDim + "_N" +dp.nSamples + "_B"+dp.nBins + "_" + dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				rt.show(title);

				String plotXaxisLabel="Estimated Radius("+unit+")";
				String plotYaxisLabel="Probability Density";						
				plot = new Plot(title,plotXaxisLabel,plotYaxisLabel);
				plot.setLimits(minBin, maxBin, minCnt, maxCnt);
				plot.setBackgroundColor(myColor);
				for(int i=1;i<rt.getLastColumn();i++)
				{
					plot.setColor("black");
					plot.setFont(myFont);
					caption = capStr +  "\n" + rt.getColumnHeading(i)+ "\nProbe "+dp.probeDim+ "\nN "+dp.nSamples+ "\nBins "+dp.nBins+"\n" + dp.valChoice;
					plot.addLabel(0.7, 0.1, caption);
					plot.setColor("red");
					plot.setLineWidth(2);
					plot.addPoints(rt.getColumn(0),rt.getColumn(i),Plot.LINE);
					plot.appendToStack();
				}
				plot.show();
				break;

			case "All Slices 3D":
				Object[]volData = imp.getStack().getImageArray();
				
				hist1= pf.poreSizeDistribution(volData,imgWidth,imgHeight,imgDepth,dp.probeDim,
							pixelWidth,pixelHeight,pixelDepth,
							dp.nBins,dp.nSamples, dp.valChoice, dp.showProgress);
				
				title = imp.getShortTitle() + "_PoreSize_"+dp.sliceChoice + "_P"+dp.probeDim + "_N"+dp.nSamples+ "_B"+dp.nBins+"_"+ dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				caption = capStr + "\n" + dp.sliceChoice+ "\nN "+dp.nSamples+ "\nBins "+dp.nBins+"\n" + dp.valChoice;

				plot = new Plot(title,"Estimated Radius("+unit+")","Probability Density");
				plot.setBackgroundColor(myColor);
				plot.setColor("black");
				plot.setFont(myFont);
				plot.addLabel(0.7, 0.1, caption);
				plot.setColor("red");
				plot.setLineWidth(2);
				plot.add("line", hist1.bin, hist1.count);
				plot.show();
				break;
			}
		}
	}
}
