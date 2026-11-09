package win.furryhook.emote;

import java.util.EnumMap;

public interface EmotePoseCarrier {
   EnumMap<EmotePart, float[]> furryhook$getEmotePose();

   void furryhook$setEmotePose(EnumMap<EmotePart, float[]> var1);
}
