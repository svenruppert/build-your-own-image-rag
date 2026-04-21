package com.svenruppert.flow.views.main;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;

/**
 * Legacy greeting view — route removed; replaced by DashboardView at path "".
 */
public class MainView
    extends VerticalLayout
    implements LocaleChangeObserver {

  public static final String YOUR_NAME = "your.name";
  public static final String SAY_HELLO = "say.hello";
  public static final String PATH = "";

  private final GreetService greetService = new GreetService();

  private final Button button = new Button();
  private final TextField textField = new TextField();

  public MainView() {
    button.addClickListener(e -> {
      add(new Paragraph(greetService.greet(textField.getValue())));
    });
    add(textField, button);
  }

  @Override
  public void localeChange(LocaleChangeEvent localeChangeEvent) {
    button.setText(getTranslation(SAY_HELLO));
    textField.setLabel(getTranslation(YOUR_NAME));
  }
}