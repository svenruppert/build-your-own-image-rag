/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.flow.views;

import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = YoutubeView.PATH, layout = MainLayout.class)
public class YoutubeView
    extends Composite<VerticalLayout> {

  public static final String PATH = "youtube";

  public YoutubeView() {
    IFrame youtubePlayer = new IFrame("https://www.youtube.com/embed/CxCMIc5Bx18");
    youtubePlayer.setWidth("800px");
    youtubePlayer.setHeight("450px");
    youtubePlayer.getElement().setAttribute("allowfullscreen", true);
    youtubePlayer.getElement().setAttribute("frameborder", "0");

    var verticalLayout = getContent();
    verticalLayout.add(youtubePlayer);
    verticalLayout.setSizeFull();
    verticalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
    verticalLayout.setAlignItems(FlexComponent.Alignment.CENTER);
  }
}