package pointFunctions;

//https://imagej.net/plugins/

import java.util.Random;
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
import jhd.PointFunctions.PointFunctions.Histogram;
import jhd.PointFunctions.*;


public class Two_Point_Correlation implements PlugInFilter, DialogListener
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
		int nSamples;
		boolean showProgress;
	}

	PointFunctions pf = new PointFunctions();
	Random myRandom;
	NumericField numSamplesNF;
	ChoiceField calcChoiceCF;

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
		//Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		if(imp.getImageStackSize()==1) sliceChoices = sliceChoices1;

		GenericDialog gd = new GenericDialog("Two Point Correlation");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.addRadioButtonGroup("Value to process", valChoices, 1, 2, valChoices[0]);
		gd.addChoice("Calculate",sliceChoices,sliceChoices[0]);
		calcChoiceCF = gda.getChoiceField(gd, null, "calcChoice");
		gd.addNumericField("Number of Samples", 10000);
		numSamplesNF = gda.getNumericField(gd, null, "numSamples");
		gd.addCheckbox("Show Progress", true);
		gd.addHelp("https://lazzyizzi.github.io/index.html");
		gd.addDialogListener(this);
		gd.setBackground(myColor);
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		DialogParams dp = new DialogParams();
		dp.valChoice = gd.getNextRadioButton();
		dp.sliceChoice = gd.getNextChoice();
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
				case "funcChoice":
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
		Histogram hist1;
		
		int	imgWidth=imp.getWidth();
		int	imgHeight=imp.getHeight();
		int	imgDepth=imp.getNSlices();
		double	pixelWidth=imp.getCalibration().pixelWidth;
		double	pixelHeight=imp.getCalibration().pixelHeight;
		double	pixelDepth=imp.getCalibration().pixelDepth;

		pf.initRandom();

		if(dp!=null)
		{
			String title,caption,capStr = "Two Point Correlation";

			switch((dp.sliceChoice))
			{
			case "Current Slice":
				sliceImp = imp.crop("whole-slice");
				Object[] image = sliceImp.getStack().getImageArray();
				imgWidth=sliceImp.getWidth();
				imgHeight=sliceImp.getHeight();
				imgDepth=sliceImp.getNSlices();

				hist1= pf.twoPointFunction(image,imgWidth,imgHeight,imgDepth,
						pixelWidth,pixelHeight,pixelDepth,
						dp.nSamples, dp.valChoice, dp.showProgress);
				sliceImp.close();
				
				title = imp.getShortTitle() + "_TwoPoint_Slice" + imp.getCurrentSlice() + "_N"+dp.nSamples+"_"+ dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				caption = capStr +  "\nSlice " +imp.getCurrentSlice()+ "\nN "+dp.nSamples+ "\n" + dp.valChoice;

				plot = new Plot(title,"Distance("+unit+")","Probability");
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
					hist1= pf.twoPointFunction(image,imgWidth,imgHeight,imgDepth,pixelWidth,pixelHeight,pixelDepth,dp.nSamples, dp.valChoice,false);

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
				title = imp.getShortTitle() + "_TwoPoint_" + dp.sliceChoice + "_N" +dp.nSamples + "_" + dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				rt.show(title);
				
				plotXaxisLabel="Length("+unit+")";
				plotYaxisLabel="Probability";						
				plot = new Plot(title,plotXaxisLabel,plotYaxisLabel);
				for(int i=1; i<=rt.getLastColumn();i++)
				{
					plot.setLimits(minBin, maxBin, minCnt, maxCnt);
					plot.setBackgroundColor(myColor);
					plot.setColor("black");
					plot.setFont(myFont);
					caption = capStr +  "\n" + rt.getColumnHeading(i)+ "\nN "+dp.nSamples+ "\n" + dp.valChoice;
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
				
				title = imp.getShortTitle() + "TwoPoint_"+dp.sliceChoice + "_N"+dp.nSamples+ "_"+ dp.valChoice +".tif";
				title = WindowManager.makeUniqueName(title);
				caption = capStr + "\n"+dp.sliceChoice+ "\nN "+dp.nSamples + "\n" + dp.valChoice;

				hist1= pf.twoPointFunction(volData,imgWidth,imgHeight,imgDepth,pixelWidth,pixelHeight,pixelDepth,dp.nSamples, dp.valChoice, dp.showProgress);
				plot = new Plot(title,"Length("+unit+")","Probability");
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

