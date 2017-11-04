
/*
Copyright (c) 2017 Donald Munro

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

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
