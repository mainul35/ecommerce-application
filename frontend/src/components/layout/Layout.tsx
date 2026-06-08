import { Outlet } from 'react-router-dom';
import { Navbar } from './Navbar';
import { Footer } from './Footer';
import { UnverifiedBanner } from './UnverifiedBanner';

export function Layout() {
  return (
    <>
      <Navbar />
      <UnverifiedBanner />
      <main>
        <Outlet />
      </main>
      <Footer />
    </>
  );
}
