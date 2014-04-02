/*
 * Copyright 2012 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.kie.workbench.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.TextBox;
import org.guvnor.common.services.shared.config.AppConfigService;
import org.guvnor.common.services.shared.config.ApplicationPreferences;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jboss.errai.ioc.client.api.AfterInitialization;
import org.jboss.errai.ioc.client.api.EntryPoint;
import org.jboss.errai.ioc.client.container.IOCBeanDef;
import org.jboss.errai.ioc.client.container.SyncBeanManager;
import org.jbpm.dashboard.renderer.service.DashboardURLBuilder;
import org.kie.workbench.client.home.HomeProducer;
import org.kie.workbench.client.resources.i18n.AppConstants;
import org.kie.workbench.common.services.security.KieWorkbenchACL;
import org.kie.workbench.common.services.security.KieWorkbenchPolicy;
import org.kie.workbench.common.services.shared.security.KieWorkbenchSecurityService;
import org.kie.workbench.common.widgets.client.resources.RoundedCornersResource;
import org.uberfire.client.UberFirePreferences;
import org.uberfire.client.menu.CustomSplashHelp;
import org.uberfire.client.mvp.AbstractWorkbenchPerspectiveActivity;
import org.uberfire.client.mvp.ActivityManager;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.workbench.widgets.menu.WorkbenchMenuBarPresenter;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.security.Identity;
import org.uberfire.security.Role;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.MenuItem;
import org.uberfire.workbench.model.menu.MenuPosition;
import org.uberfire.workbench.model.menu.Menus;

import static org.kie.workbench.client.security.KieWorkbenchFeatures.*;

/**
 *
 */
@EntryPoint
public class KieWorkbenchEntryPoint {

    private AppConstants constants = AppConstants.INSTANCE;

    @Inject
    private PlaceManager placeManager;

    @Inject
    private WorkbenchMenuBarPresenter menubar;

    @Inject
    private ActivityManager activityManager;

    @Inject
    private SyncBeanManager iocManager;

    @Inject
    public Identity identity;

    @Inject
    private Caller<AppConfigService> appConfigService;

    @Inject
    private HomeProducer homeProducer;

    @Inject
    private KieWorkbenchACL kieACL;

    @Inject
    private Caller<KieWorkbenchSecurityService> kieSecurityService;

    private SuggestBox actionText;
    private TextBox textSuggestBox;
    DefaultSuggestionDisplay suggestionDisplay;
    Map<String, String> actions = new HashMap<String, String>();

    @AfterInitialization
    public void startApp() {
        kieSecurityService.call(new RemoteCallback<String>() {
            public void callback(final String str) {
                KieWorkbenchPolicy policy = new KieWorkbenchPolicy(str);
                kieACL.activatePolicy(policy);
                loadPreferences();
                loadStyles();
                setupMenu();
                hideLoadingPopup();
                homeProducer.init();
            }
        }).loadPolicy();
    }

    private void loadPreferences() {
        UberFirePreferences.setProperty( "org.uberfire.client.workbench.widgets.listbar.context.disable", true );
        appConfigService.call( new RemoteCallback<Map<String, String>>() {
            @Override
            public void callback( final Map<String, String> response ) {
                ApplicationPreferences.setUp( response );
            }
        } ).loadPreferences();
    }

    private void loadStyles() {
        //Ensure CSS has been loaded
        //ShowcaseResources.INSTANCE.showcaseCss().ensureInjected();
        RoundedCornersResource.INSTANCE.roundCornersCss().ensureInjected();
    }

    private void setupMenu() {

        final AbstractWorkbenchPerspectiveActivity defaultPerspective = getDefaultPerspectiveActivity();

        final Menus menus = MenuFactory.newTopLevelMenu(constants.Home()).respondsWith(new Command() {
            @Override
            public void execute() {
                if (defaultPerspective != null) {
                    placeManager.goTo(new DefaultPlaceRequest(defaultPerspective.getIdentifier()));
                } else {
                    Window.alert(constants.missingDefaultPerspective());
                }
            }
        })
                .endMenu()
                .newTopLevelMenu(constants.Authoring()).withRoles(kieACL.getGrantedRoles(G_AUTHORING)).withItems(getAuthoringViews()).endMenu()
                .newTopLevelMenu(constants.Deploy()).withRoles(kieACL.getGrantedRoles(G_DEPLOY)).withItems(getDeploymentViews()).endMenu()
                .newTopLevelMenu(constants.Process_Management()).withRoles(kieACL.getGrantedRoles(G_PROCESS_MANAGEMENT)).withItems(getProcessMGMTViews()).endMenu()
                .newTopLevelMenu(constants.Tasks()).withRoles(kieACL.getGrantedRoles(G_TASKS)).withItems(getTasksViews()).endMenu()
                .newTopLevelMenu(constants.Dashboards()).withRoles(kieACL.getGrantedRoles(G_DASHBOARDS)).withItems(getDashboardViews()).endMenu()
                .newTopLevelMenu(constants.find()).withRoles(kieACL.getGrantedRoles(F_SEARCH)).position( MenuPosition.RIGHT ).respondsWith( new Command() {
                    @Override
                    public void execute() {
                        placeManager.goTo( "FindForm" );
                    }
                } )
                .endMenu()
                .newTopLevelMenu(constants.User() + ": " + identity.getName())
                .position(MenuPosition.RIGHT)
                .withItems(getRoles())
                .endMenu()
                .newTopLevelCustomMenu( iocManager.lookupBean( CustomSplashHelp.class ).getInstance() )
                .endMenu()
                .build();

        menubar.addMenus(menus);
    }

    private List<? extends MenuItem> getRoles() {
        final List<MenuItem> result = new ArrayList<MenuItem>( identity.getRoles().size() );
        for ( final Role role : identity.getRoles() ) {
            if(!role.getName().equals("IS_REMEMBER_ME")){
                result.add( MenuFactory.newSimpleItem( constants.Role() +": " + role.getName() ).endMenu().build().getItems().get( 0 ) );
            }
        }
        result.add(MenuFactory.newSimpleItem(constants.LogOut()).respondsWith(new Command() {
                    @Override
                    public void execute() {
                        redirect(GWT.getModuleBaseURL() + "uf_logout");
                    }
                }).endMenu().build().getItems().get(0));

        return result;
    }

    private List<? extends MenuItem> getAuthoringViews() {
        final List<MenuItem> result = new ArrayList<MenuItem>( 1 );

        result.add(MenuFactory.newSimpleItem(constants.Project_Authoring()).withRoles(kieACL.getGrantedRoles(F_PROJECT_AUTHORING)).respondsWith(new Command() {
            @Override
            public void execute() {
                placeManager.goTo(new DefaultPlaceRequest("org.kie.workbench.client.perspectives.DroolsAuthoringPerspective"));
            }
        }).endMenu().build().getItems().get(0));

        result.add( MenuFactory.newSimpleItem( constants.artifactRepository() ).withRoles(kieACL.getGrantedRoles( F_ARTIFACT_REPO )).respondsWith( new Command() {
            @Override
            public void execute() {
                placeManager.goTo( new DefaultPlaceRequest( "org.guvnor.m2repo.client.perspectives.GuvnorM2RepoPerspective" ) );
            }
        } ).endMenu().build().getItems().get( 0 ) );

        result.add(MenuFactory.newSimpleItem(constants.Administration()).withRoles(kieACL.getGrantedRoles(F_ADMINISTRATION)).respondsWith(new Command() {
            @Override
            public void execute() {
                placeManager.goTo(new DefaultPlaceRequest("org.kie.workbench.client.perspectives.AdministrationPerspective"));
            }
        }).endMenu().build().getItems().get(0));

        return result;
    }

    private List<? extends MenuItem> getProcessMGMTViews() {
        final List<MenuItem> result = new ArrayList<MenuItem>( 2 );

        result.add(MenuFactory.newSimpleItem(constants.Process_Definitions()).withRoles(kieACL.getGrantedRoles(F_PROCESS_DEFINITIONS)).respondsWith(new Command() {
            @Override
            public void execute() {
                placeManager.goTo(new DefaultPlaceRequest("Process Definitions"));
            }
        }).endMenu().build().getItems().get(0));

        result.add( MenuFactory.newSimpleItem( constants.Process_Instances() ).withRoles(kieACL.getGrantedRoles(F_PROCESS_INSTANCES)).respondsWith( new Command() {
            @Override
            public void execute() {
                placeManager.goTo( new DefaultPlaceRequest( "Process Instances" ) );
            }
        } ).endMenu().build().getItems().get( 0 ) );

        return result;
    }

    private List<? extends MenuItem> getDeploymentViews() {
        final List<MenuItem> result = new ArrayList<MenuItem>( 1 );

        result.add(MenuFactory.newSimpleItem(constants.Deployments()).withRoles(kieACL.getGrantedRoles(F_DEPLOYMENTS)).respondsWith(new Command() {
            @Override
            public void execute() {
                placeManager.goTo(new DefaultPlaceRequest("Deployments"));
            }
        } ).endMenu().build().getItems().get( 0 ) );

        return result;
    }

    private List<? extends MenuItem> getTasksViews() {
        final List<MenuItem> result = new ArrayList<MenuItem>( 2 );

        result.add(MenuFactory.newSimpleItem(constants.Tasks_List()).withRoles(kieACL.getGrantedRoles(F_TASKS)).respondsWith(new Command() {
            @Override
            public void execute() {
                placeManager.goTo(new DefaultPlaceRequest("Tasks"));
            }
        }).endMenu().build().getItems().get(0));

        return result;
    }

    private List<? extends MenuItem> getDashboardViews() {
        final List<MenuItem> result = new ArrayList<MenuItem>( 1 );
        result.add(MenuFactory.newSimpleItem(constants.Process_Dashboard()).withRoles(kieACL.getGrantedRoles(F_PROCESS_DASHBOARD)).respondsWith(new Command() {

            @Override
            public void execute() {
                placeManager.goTo("DashboardPerspective");
            }
        }).endMenu().build().getItems().get(0));

        final String dashbuilderURL = DashboardURLBuilder.getDashboardURL("/dashbuilder/workspace", "showcase", LocaleInfo.getCurrentLocale());
        result.add( MenuFactory.newSimpleItem( constants.Business_Dashboard() ).withRoles(kieACL.getGrantedRoles(F_DASHBOARD_BUILDER)).respondsWith( new Command() {
            @Override
            public void execute() {
                Window.open( dashbuilderURL , "_blank", "" );
            }
        } ).endMenu().build().getItems().get( 0 ) );

        return result;
    }

    private AbstractWorkbenchPerspectiveActivity getDefaultPerspectiveActivity() {
        AbstractWorkbenchPerspectiveActivity defaultPerspective = null;
        final Collection<IOCBeanDef<AbstractWorkbenchPerspectiveActivity>> perspectives = iocManager.lookupBeans( AbstractWorkbenchPerspectiveActivity.class );
        final Iterator<IOCBeanDef<AbstractWorkbenchPerspectiveActivity>> perspectivesIterator = perspectives.iterator();
        outer_loop:
        while ( perspectivesIterator.hasNext() ) {
            final IOCBeanDef<AbstractWorkbenchPerspectiveActivity> perspective = perspectivesIterator.next();
            final AbstractWorkbenchPerspectiveActivity instance = perspective.getInstance();
            if ( instance.isDefault() ) {
                defaultPerspective = instance;
                break outer_loop;
            } else {
                iocManager.destroyBean( instance );
            }
        }
        return defaultPerspective;
    }


    //Fade out the "Loading application" pop-up
    private void hideLoadingPopup() {
        final Element e = RootPanel.get( "loading" ).getElement();

        new Animation() {
            @Override
            protected void onUpdate( double progress ) {
                e.getStyle().setOpacity( 1.0 - progress );
            }

            @Override
            protected void onComplete() {
                e.getStyle().setVisibility( Style.Visibility.HIDDEN );
            }
        }.run( 500 );
    }

    public static native void redirect( String url )/*-{
        $wnd.location = url;
    }-*/;

}
