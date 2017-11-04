package to.ar.tango.tangocamera;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

public class PrefActivity extends PreferenceActivity
//==================================================
{
   @Override
   protected void onCreate(Bundle savedInstanceState)
   //------------------------------------------------
   {
      super.onCreate(savedInstanceState);
      getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefFragment()).commit();

   }

   public static class PrefFragment extends PreferenceFragment
   //=========================================================
   {

      @Override
      public void onCreate(final Bundle savedInstanceState)
      //---------------------------------------------------
      {
         super.onCreate(savedInstanceState);
         addPreferencesFromResource(R.xml.preferences);
         setHasOptionsMenu(true);
         Preference confidenceText = findPreference("confidence");
         confidenceText.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
         //======================================================================================
         {
            @Override public boolean onPreferenceChange(Preference preference, Object newValue)
            //--------------------------------------------------------------------------------
            {
               if (newValue == null)
                  return false;
               float v;
               try { v = Float.parseFloat(newValue.toString()); } catch (Exception e) { v = -1; }
               return ( (v >= 0.0) && (v <= 1.0) );
            }
         });
         Preference doneButton = findPreference("done");
         doneButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
         //================================================================================
         {
            @Override
            public boolean onPreferenceClick(Preference preference)
            //-----------------------------------------------------
            {
               PrefFragment.this.getActivity().finish();
               return true;
            }
         });
      }
   }
}
