package ogles.oglbackbone.nnet;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Iterator;

public abstract class NNetParser {

    private final static String TAG = "NNetParser";

    public static String getHumanReadableDescription(MLPNet mlp){

        if(mlp==null)
            return "Invalid network";

        String out = "";

        out += ("Num connected layers " + mlp.layers.length);
        out += ("\nsize of input buf " + mlp.layers[0].input);
        out += ("\nsize of output buf " + mlp.layers[mlp.layers.length-1].output);
        out += ("\nlargest perceptron allocation " + mlp.largestLayer);
        out += ("\ntotal weights " + mlp.totalWeightSize);

        return out;
    }

    public static MLPNet parseNet(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuffer src = new StringBuffer();

        final Iterator<String> it = reader.lines().iterator();
        while( it.hasNext() )
            src.append(it.next()+"\n");

        in.close();
        reader.close();

        return parseNet(src.toString());
    }

    public static MLPNet parseNet(String in) throws IOException  {
        BufferedReader reader = new BufferedReader(new StringReader(in));

        MLPNet mlp = null;
        int layerIndex = -1;
        int curWeight = 0;

        while(reader.ready()){

            String line = reader.readLine();

            Log.v(TAG,"doing line " + line);

            if(line==null)
                break;

            if(line.equals("\n") || line.length()==0)
                continue;

            if(mlp==null){
                if(!line.equals("MLP")){
                    //error here!
                    break;
                }
                else {
                    mlp = new MLPNet();
                    continue;
                }
            }

            if(line.equals("sigmoid")){

                layerIndex++;
                curWeight=0;

                mlp.layers[layerIndex] = new NLayer();
                mlp.layers[layerIndex].type="sigmoid";
                continue;
            }

            if(mlp.layers == null){
                mlp.layers = new NLayer[Integer.parseInt(line)];
                continue;
            }

            if(mlp.layers[layerIndex].input==0){
                mlp.layers[layerIndex].input = Integer.parseInt(line);
                continue;
            }

            if(mlp.layers[layerIndex].output==0){
                mlp.layers[layerIndex].output = Integer.parseInt(line);
                continue;
            }

            if(mlp.layers[layerIndex].output!=0 && mlp.layers[layerIndex].input!=0){
                if(mlp.layers[layerIndex].weights==null){
                    mlp.layers[layerIndex].weights = new float[mlp.layers[layerIndex].output*mlp.layers[layerIndex].input];
					/*if(mlp.layers[layerIndex].weights.length>mlp.largestLayer)
						mlp.largestLayer = mlp.layers[layerIndex].weights.length;*/
                    if(mlp.layers[layerIndex].input > mlp.largestLayer) mlp.largestLayer = mlp.layers[layerIndex].input;
                    if(mlp.layers[layerIndex].output > mlp.largestLayer) mlp.largestLayer = mlp.layers[layerIndex].output;
                }
                mlp.layers[layerIndex].weights[curWeight] = Float.parseFloat(line);
                curWeight++;
                mlp.totalWeightSize++;
            }

        }

        return mlp;

    }


}
