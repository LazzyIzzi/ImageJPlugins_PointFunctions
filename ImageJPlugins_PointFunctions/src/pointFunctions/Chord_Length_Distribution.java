package pointFunctions;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Font;
import java.awt.TextField;
import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.ChoiceField;
import jhd.ImageJAddins.GenericDialogAddin.NumericField;
import jhd.PointFunctions.PointFunctions;
import jhd.ProgressBars.ProgressBars;

public class Chord_Length_Distribution implements PlugInFilter, DialogListener
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
		boolean showProgress;
	}

	//ProgressBars progBars = new ProgressBars("Chord Length Distribution");
	PointFunctions pf = new PointFunctions();
	NumericField numBinsNF,numSamplesNF;
	ChoiceField calcChoiceCF,funcChoiceCF;

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
		if(imgDepth==1) sliceChoices = sliceChoices1;

		maxR=pf.getMaxPixelSeparationDistance(imgWidth, imgHeight, imgDepth);

		GenericDialog gd = new GenericDialog("Chord Length Distribution");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.addRadioButtonGroup("Value to process", valChoices, 1, 2, valChoices[0]);
		gd.addChoice("Calculate",sliceChoices,sliceChoices[0]);
		calcChoiceCF = gda.getChoiceField(gd, null, "calcChoice");
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
	@Override
	public void run(ImageProcessor ip) {

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
			String title,caption, capStr = "Chord Length Distribution";
	
			switch((dp.sliceChoice))
			{
			case "Current Slice":
				sliceImp = imp.crop("whole-slice");
				Object[] image = sliceImp.getStack().getImageArray();
				imgWidth=sliceImp.getWidth();
				imgHeight=sliceImp.getHeight();
				imgDepth=sliceImp.getNSlices();

				hist1= pf.chordLengthDistribution(image,imgWidth,imgHeight,imgDepth,
						pixelWidth,pixelHeight,pixelDepth,
						dp.nBins,dp.nSamples, dp.valChoice, dp.showProgress);
				sliceImp.close();
				
				title = imp.getShortTitle() + "_ChordLen_Slice_" + imp.getCurrentSlice() + "_N"+dp.nSamples+ "_B"+dp.nBins+"_"+ dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				caption = capStr + "\nSlice " +imp.getCurrentSlice()+ "\nN "+dp.nSamples+ "\nBins "+dp.nBins+"\n" + dp.valChoice;

				plot = new Plot(title,"Length("+unit+")","Probability Density");
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
				String plotXaxisLabel=null, plotYaxisLabel=null;

				int slices = imp.getStack().getSize();
				for(int i = 0; i<slices; i++)
				{
					imp.setSlice(i+1);
					sliceImp = imp.crop("whole-slice");
					image = sliceImp.getStack().getImageArray();
					imgWidth=sliceImp.getWidth();
					imgHeight=sliceImp.getHeight();
					imgDepth=sliceImp.getNSlices();

					hist1= pf.chordLengthDistribution(image,imgWidth,imgHeight,imgDepth,
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
				title = imp.getShortTitle() + "_ChordLen_" + dp.sliceChoice + "_N" +dp.nSamples + "_B"+dp.nBins + "_" + dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				rt.show(title);

				plotXaxisLabel="Length("+unit+")";
				plotYaxisLabel="Probability Density";										
				plot = new Plot(title,plotXaxisLabel,plotYaxisLabel);
				plot.setLimits(minBin, maxBin, minCnt, maxCnt);
				plot.setBackgroundColor(myColor);
				for(int i=1;i<=rt.getLastColumn();i++)
				{
					plot.setColor("black");
					plot.setFont(myFont);
					caption = capStr + "\n" + rt.getColumnHeading(i)+ "\nN "+dp.nSamples+ "\nBins "+dp.nBins+"\n" + dp.valChoice;
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

				hist1= pf.chordLengthDistribution(volData,imgWidth,imgHeight,imgDepth,pixelWidth,
						pixelHeight,pixelDepth,dp.nBins,dp.nSamples, dp.valChoice, dp.showProgress);
				

				title = imp.getShortTitle() +"_ChordLen_"+ dp.sliceChoice + "_N"+dp.nSamples+ "_B"+dp.nBins+"_"+ dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				caption = capStr +  "\n" + dp.sliceChoice+ "\nN "+dp.nSamples+ "\nBins "+dp.nBins+"\n" + dp.valChoice;
											
				plot = new Plot(title,"Length("+unit+")","Probability Density");
				plot.setColor("black");
				plot.setFont(myFont);
				plot.addLabel(0.7, 0.1, caption);
				plot.setBackgroundColor(myColor);
				plot.setColor("red");
				plot.setLineWidth(2);
				plot.add("line", hist1.bin, hist1.count);
				plot.show();
				break;
			}
		}
	
	}

}
