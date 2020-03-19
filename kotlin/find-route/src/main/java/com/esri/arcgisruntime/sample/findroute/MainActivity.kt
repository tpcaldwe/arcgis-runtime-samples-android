/* Copyright 2017 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.esri.arcgisruntime.sample.findroute

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.ArcGISVectorTiledLayer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.tasks.networkanalysis.Route
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask
import com.esri.arcgisruntime.tasks.networkanalysis.Stop
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutionException

class MainActivity : AppCompatActivity() {

  private val TAG = MainActivity::class.java.simpleName

  private val graphicsOverlay: GraphicsOverlay by lazy {
    // create a graphics overlay and add it to the map view
    GraphicsOverlay().also {
      mapView.graphicsOverlays.add(it)
    }
  }
  private val drawerToggle: ActionBarDrawerToggle by lazy { setupDrawer() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    // create new basemap with the vector tiled layer from a service url
    val basemap = Basemap(
      ArcGISVectorTiledLayer(resources.getString(R.string.navigation_vector))
    )
    // create a map with the basemap
    ArcGISMap(basemap).let { map ->
      // set initial viewpoint to San Diego
      map.initialViewpoint = Viewpoint(32.7157, -117.1611, 200000.0)
      // set the map to be displayed in this view
      mapView.map = map
    }
    // update UI when attribution view changes
    val params = directionFab.layoutParams as ConstraintLayout.LayoutParams
    mapView.addAttributionViewLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
      val heightDelta = bottom - oldBottom
      params.bottomMargin += heightDelta
    }

    setupSymbols()

    directionFab.setOnClickListener {

      //TODO: figure out what this does
      supportActionBar?.apply {
        setDisplayHomeAsUpEnabled(true)
        setHomeButtonEnabled(true)
        title = getString(R.string.app_name)
      }

      solveRoute()
    }
  }

  private fun solveRoute() {
    // create a route task instance
    val routeTask = RouteTask(this, getString(R.string.routing_service))
    // create an alert dialog for showing load progress
    val progressDialog = createProgressDialog(routeTask)
    progressDialog.show()

    val listenableFuture = routeTask.createDefaultParametersAsync()
    listenableFuture.addDoneListener {
      try {
        if (listenableFuture.isDone) {
          val routeParams = listenableFuture.get()
          // create stops
          val stops = arrayListOf(
            Stop(Point(-117.15083257944445, 32.741123367963446, SpatialReferences.getWgs84())),
            Stop(Point(-117.15557279683529, 32.703360305883045, SpatialReferences.getWgs84()))
          )
          routeParams.apply {
            setStops(stops)
            // set return directions as true to return turn-by-turn directions in the result of
            // getDirectionManeuvers().
            isReturnDirections = true
          }

          // solve
          val result = routeTask.solveRouteAsync(routeParams).get()
          val route = result.routes[0] as Route
          // create a simple line symbol for the route
          val routeSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 5f)
          // create a graphic for the route
          Graphic(route.routeGeometry, routeSymbol).also {
            // add the graphic to the map
            graphicsOverlay.graphics.add(it)
          }

          // get directions
          // NOTE: to get turn-by-turn directions Route Parameters should set returnDirection flag as true
          val directions = route.directionManeuvers
          val directionsArray = Array<String>(directions.size) { i ->
            directions[i].directionText
          }

          if (progressDialog.isShowing) {
            progressDialog.dismiss()
          }

          left_drawer.apply {
          // Set the adapter for the list view
          adapter = ArrayAdapter(
            applicationContext,
            R.layout.directions_layout,
            directionsArray
          )

          onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
              if (graphicsOverlay.graphics.size > 3) {
                graphicsOverlay.graphics.removeAt(graphicsOverlay.graphics.size - 1)
              }

              drawer_layout.closeDrawers()

              val geometry = directions[position].geometry
              mapView.setViewpointAsync(
                Viewpoint(geometry.extent, 20.0),
                3f
              )
              // create a graphic with a symbol for the route and add it to the graphics overlay
              val selectedRouteSymbol = SimpleLineSymbol(
                SimpleLineSymbol.Style.SOLID,
                Color.GREEN, 5f
              )
              Graphic(geometry, selectedRouteSymbol).also {
                graphicsOverlay.graphics.add(it)
              }
            }
        }
        } //TODO: should there be an else here?
      } catch (e: Exception) {
        Log.e(TAG, "${e.message}")
      }
    }
  }


  /**
   * Set up the Source, Destination and routeSymbol graphics symbol
   */
  private fun setupSymbols() {
    //[DocRef: Name=Picture Marker Symbol Drawable-android, Category=Fundamentals, Topic=Symbols and Renderers]
    // Create a picture marker symbol from an app resource
    try {
      val startDrawable =
        ContextCompat.getDrawable(this, R.drawable.ic_source) as BitmapDrawable?
      val pinSourceSymbol = PictureMarkerSymbol.createAsync(startDrawable).get()
      pinSourceSymbol.loadAsync()
      pinSourceSymbol.addDoneLoadingListener {
        // add a new graphic as start point
        val sourcePoint = Point(
          -117.15083257944445,
          32.741123367963446,
          SpatialReferences.getWgs84()
        )
        Graphic(sourcePoint, pinSourceSymbol).also {
          graphicsOverlay.graphics.add(it)
        }
      }
      pinSourceSymbol.offsetY = 20f
    } catch (e: InterruptedException) {
      e.printStackTrace()
    } catch (e: ExecutionException) {
      e.printStackTrace()
    }
    //[DocRef: END]
    try {
      val endDrawable =
        ContextCompat.getDrawable(this, R.drawable.ic_destination) as BitmapDrawable?
      val pinDestinationSymbol = PictureMarkerSymbol.createAsync(endDrawable).get()
      pinDestinationSymbol.loadAsync()
      pinDestinationSymbol.addDoneLoadingListener {
        // add a new graphic as end point
        val destinationPoint = Point(
          -117.15557279683529,
          32.703360305883045,
          SpatialReferences.getWgs84()
        )
        Graphic(destinationPoint, pinDestinationSymbol).also {
          graphicsOverlay.graphics.add(it)
        }
      }
      pinDestinationSymbol.offsetY = 20f
    } catch (e: InterruptedException) {
      e.printStackTrace()
    } catch (e: ExecutionException) {
      e.printStackTrace()
    }
    //[DocRef: END]
  }

  /**
   * set up the drawer
   */
  private fun setupDrawer() =
    object : ActionBarDrawerToggle(
      this,
      drawer_layout,
      R.string.drawer_open,
      R.string.drawer_close
    ) {
      /** Called when a drawer has settled in a completely open state.  */
      override fun onDrawerOpened(drawerView: View) {
        super.onDrawerOpened(drawerView)
        invalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
      }

      /** Called when a drawer has settled in a completely closed state.  */
      override fun onDrawerClosed(view: View) {
        super.onDrawerClosed(view)
        invalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
      }
    }.apply {
      isDrawerIndicatorEnabled = true
      drawer_layout.addDrawerListener(this)
      drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }



  /** Create a progress dialog box for tracking the route task.
   *
   * @param routeTask the route task progress to be tracked
   * @return an AlertDialog set with the dialog layout view
   */
  private fun createProgressDialog(routeTask: RouteTask): AlertDialog {
    val builder = AlertDialog.Builder(this@MainActivity).apply {
      setTitle("Solving route...")
      // provide a cancel button on the dialog
      setNeutralButton("Cancel") { _, _ ->
        routeTask.cancelLoad()
      }
      setCancelable(false)
      setView(R.layout.dialog_layout)
    }
    return builder.create()
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)
    // Sync the toggle state after onRestoreInstanceState has occurred.
    drawerToggle.syncState()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
//    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    super.onConfigurationChanged(newConfig)
    drawerToggle.onConfigurationChanged(newConfig)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean { // Inflate the menu; this adds items to the action bar if it is present.
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean { // Handle action bar item clicks here. The action bar will
// automatically handle clicks on the Home/Up button, so long
// as you specify a parent activity in AndroidManifest.xml.
// Activate the navigation drawer toggle
    return drawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item)
  }

  override fun onPause() {
    mapView.pause()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    mapView.resume()
  }

  override fun onDestroy() {
    mapView.dispose()
    super.onDestroy()
  }
}